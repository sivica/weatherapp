import base64
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.error
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from private_build import trusted_context, version_code, publish_guard, materialize, cleanup, build
from publish_internal import Client, NoRedirect, PublishError, publish, BASE, UPLOAD, PACKAGE, edit_id

TRUST={'GITHUB_ACTIONS':'true','GITHUB_REPOSITORY':'sivica/weatherapp','GITHUB_REF':'refs/heads/main','GITHUB_EVENT_NAME':'workflow_dispatch','GITHUB_ACTOR_ID':'61886','GITHUB_REPOSITORY_ID':'338999765','GITHUB_REPOSITORY_OWNER_ID':'61886','GITHUB_WORKFLOW_REF':'sivica/weatherapp/.github/workflows/internal-play.yml@refs/heads/main','CI_VERSION_CODE':'2','UPLOAD_TO_INTERNAL':'false'}

class PrivateBuildTests(unittest.TestCase):
 def test_only_owner_manual_main_context_is_trusted(self):
  trusted_context(TRUST)
  for key,value in [('GITHUB_REF','refs/heads/feature'),('GITHUB_EVENT_NAME','pull_request'),('GITHUB_REPOSITORY','fork/weatherapp'),('GITHUB_ACTOR_ID','1'),('GITHUB_REPOSITORY_ID','1'),('GITHUB_REPOSITORY_OWNER_ID','1'),('GITHUB_WORKFLOW_REF','sivica/weatherapp/.github/workflows/other.yml@refs/heads/main')]:
   with self.subTest(key=key),self.assertRaises(ValueError):trusted_context(dict(TRUST,**{key:value}))
 def test_version_is_bounded_and_not_shell_input(self):
  self.assertEqual(2,version_code('2'))
  for value in ('0','1','-1','2;echo injected','2100000001','1.0',''):
   with self.assertRaises(ValueError):version_code(value)
 def test_build_only_needs_no_publishing_identity(self):publish_guard(TRUST)
 def test_publish_requires_explicit_bootstrap_identity_and_tester_confirmation(self):
  with self.assertRaises(ValueError):publish_guard(dict(TRUST,UPLOAD_TO_INTERNAL='true'))
  ready=dict(TRUST,UPLOAD_TO_INTERNAL='true',PLAY_PUBLISH_READY='true',PLAY_APP_BOOTSTRAPPED='true',OWNER_ONLY_TESTERS_CONFIRMED='true',PLAY_WIF_PROVIDER='fixture-provider',PLAY_SERVICE_ACCOUNT='fixture@example.invalid')
  publish_guard(ready)
  for key in ('PLAY_PUBLISH_READY','PLAY_APP_BOOTSTRAPPED','OWNER_ONLY_TESTERS_CONFIRMED','PLAY_WIF_PROVIDER','PLAY_SERVICE_ACCOUNT'):
   with self.subTest(key=key),self.assertRaises(ValueError):publish_guard(dict(ready,**{key:''}))
 def fixture_env(self,temp,include='true'):
  return dict(TRUST,RUNNER_TEMP=temp,INCLUDE_WEATHER_KEY=include,WEATHERAPP_UPLOAD_KEYSTORE_B64=base64.b64encode(b'fixture-keystore').decode(),WEATHERAPP_UPLOAD_PASSWORD='FixturePassword012345',WEATHERAPP_WEATHER_API_KEY='FixtureWeatherKey0123456789ABCDEF')
 def test_materializes_owner_only_without_console_output(self):
  with tempfile.TemporaryDirectory() as temp:
   capture=io.StringIO()
   with contextlib.redirect_stdout(capture),contextlib.redirect_stderr(capture):private=materialize(self.fixture_env(temp))
   self.assertEqual('',capture.getvalue());self.assertEqual(0o700,private.stat().st_mode&0o777)
   self.assertEqual({'upload.p12','password.txt','weather-key.txt'},{p.name for p in private.iterdir()})
   self.assertTrue(all(p.stat().st_mode&0o777==0o600 for p in private.iterdir()))
 def test_key_free_mode_does_not_write_weather_input(self):
  with tempfile.TemporaryDirectory() as temp:
   private=materialize(self.fixture_env(temp,'false'));self.assertFalse((private/'weather-key.txt').exists())
 def test_invalid_secret_input_cleans_partial_private_files(self):
  with tempfile.TemporaryDirectory() as temp:
   with self.assertRaises(ValueError):materialize(dict(self.fixture_env(temp),WEATHERAPP_WEATHER_API_KEY='bad\nvalue'))
   self.assertEqual([],list(Path(temp).iterdir()))
 def test_password_file_line_terminator_is_normalized_without_changing_password(self):
  with tempfile.TemporaryDirectory() as temp:
   private=materialize(dict(self.fixture_env(temp),WEATHERAPP_UPLOAD_PASSWORD='FixturePassword012345\n'))
   self.assertEqual(b'FixturePassword012345',(private/'password.txt').read_bytes())
 def test_internal_newline_in_password_is_rejected(self):
  with tempfile.TemporaryDirectory() as temp:
   with self.assertRaises(ValueError):materialize(dict(self.fixture_env(temp),WEATHERAPP_UPLOAD_PASSWORD='Fixture\nPassword'))
   self.assertEqual([],list(Path(temp).iterdir()))
 def test_cleanup_preserves_unrelated_files_and_rejects_outside_path(self):
  with tempfile.TemporaryDirectory() as temp:
   root=Path(temp);private=root/'weatherapp-private-fixture';private.mkdir();(private/'secret').write_text('fixture')
   workspace=root/'workspace';(workspace/'app/build').mkdir(parents=True);home=root/'weatherapp-gradle';home.mkdir();other=root/'unrelated';other.write_text('preserve')
   env=dict(TRUST,RUNNER_TEMP=temp,GITHUB_WORKSPACE=str(workspace),GRADLE_USER_HOME=str(home),WEATHERAPP_PRIVATE_DIR=str(private))
   with self.assertRaises(ValueError):cleanup(dict(env,WEATHERAPP_PRIVATE_DIR='/tmp/unrelated'))
   cleanup(env);self.assertTrue(other.exists());self.assertFalse(private.exists());self.assertFalse(home.exists())
 def test_cleanup_refuses_local_non_ci_context(self):
  with self.assertRaises(ValueError):cleanup({})
 def test_private_build_failure_never_prints_captured_diagnostics(self):
  import subprocess
  capture=io.StringIO()
  result=subprocess.CompletedProcess([],1,stdout='FixtureSecretCompilerOutput',stderr='FixtureSensitiveError')
  with patch('private_build.subprocess.run',return_value=result) as run,contextlib.redirect_stdout(capture),contextlib.redirect_stderr(capture):
   with self.assertRaises(RuntimeError) as error:build(Path('/fixture'),Path('/fixture-private'),dict(TRUST,INCLUDE_WEATHER_KEY='true'))
  self.assertEqual('',capture.getvalue());self.assertNotIn('FixtureSecret',str(error.exception))
  self.assertTrue(run.call_args.kwargs['capture_output'])
  self.assertTrue(any('weather-key.txt' in arg for arg in run.call_args.args[0]))
 def test_private_build_key_free_mode_never_passes_key_path(self):
  import subprocess
  result=subprocess.CompletedProcess([],0,stdout='private diagnostics',stderr='')
  with patch('private_build.subprocess.run',return_value=result) as run,contextlib.redirect_stdout(io.StringIO()):build(Path('/fixture'),Path('/fixture-private'),dict(TRUST,INCLUDE_WEATHER_KEY='false'))
  self.assertFalse(any('weatherappCiKeyFile' in arg for arg in run.call_args.args[0]))

class FakePlay:
 def __init__(self,existing=False,wrong_version=False,commit_uncertain=False,higher_track=False):self.calls=[];self.count=0;self.existing=existing;self.wrong_version=wrong_version;self.commit_uncertain=commit_uncertain;self.higher_track=higher_track
 def call(self,method,url,payload=None,binary=None):
  self.calls.append((method,url,payload,binary))
  if url==BASE+'/edits' and method=='POST':self.count+=1;return {'id':'edit'+str(self.count)}
  if url.endswith('/bundles') and method=='GET':return {'bundles':[{'versionCode':2}]} if self.existing else {}
  if url.endswith('/tracks') and method=='GET':return {'tracks':[{'track':'internal','releases':[{'versionCodes':['9']}]}]} if self.higher_track else {}
  if url.startswith(UPLOAD):return {'versionCode':3 if self.wrong_version else 2}
  if url.endswith(':commit') and self.commit_uncertain:raise PublishError('Commit outcome uncertain')
  if url.endswith('/tracks/internal') and method=='GET':return {'releases':[{'status':'completed','versionCodes':['2']} ]}
  return {}

class PublisherTests(unittest.TestCase):
 def run_publish(self,client,package=PACKAGE):
  with tempfile.TemporaryDirectory() as temp:
   bundle=Path(temp)/'fixture.aab';bundle.write_bytes(b'fixture-only')
   with contextlib.redirect_stdout(io.StringIO()):publish(client,bundle,{'package':package,'versionCode':2})
 def test_fixed_internal_track_and_verified_commit(self):
  client=FakePlay();self.run_publish(client)
  tracks=[c for c in client.calls if c[0]=='PUT'];self.assertEqual(1,len(tracks));self.assertTrue(tracks[0][1].endswith('/tracks/internal'));self.assertEqual('internal',tracks[0][2]['track']);self.assertEqual('completed',tracks[0][2]['releases'][0]['status'])
  self.assertTrue(any(c[0]=='DELETE' and c[1].endswith('/edit2') for c in client.calls));self.assertFalse(any('production' in c[1] for c in client.calls))
 def test_other_package_never_calls_play(self):
  client=FakePlay()
  with self.assertRaises(PublishError):self.run_publish(client,'another.app')
  self.assertEqual([],client.calls)
 def test_existing_version_stops_before_upload_and_cleans_edit(self):
  client=FakePlay(existing=True)
  with self.assertRaises(PublishError):self.run_publish(client)
  self.assertFalse(any(c[1].startswith(UPLOAD) for c in client.calls));self.assertEqual('DELETE',client.calls[-1][0])
 def test_higher_released_version_stops_before_upload(self):
  client=FakePlay(higher_track=True)
  with self.assertRaises(PublishError):self.run_publish(client)
  self.assertFalse(any(c[1].startswith(UPLOAD) for c in client.calls));self.assertEqual('DELETE',client.calls[-1][0])
 def test_server_edit_id_cannot_redirect_followup_requests(self):
  for value in ('../other','x?redirect=evil','x/y','',None):
   with self.assertRaises(PublishError):edit_id(value)
 def test_upload_version_mismatch_never_commits(self):
  client=FakePlay(wrong_version=True)
  with self.assertRaises(PublishError):self.run_publish(client)
  self.assertFalse(any(c[1].endswith(':commit') for c in client.calls));self.assertEqual('DELETE',client.calls[-1][0])
 def test_uncertain_commit_is_not_retried_or_blindly_deleted(self):
  client=FakePlay(commit_uncertain=True)
  with self.assertRaises(PublishError):self.run_publish(client)
  self.assertEqual(1,sum(c[1].endswith(':commit') for c in client.calls));self.assertFalse(any(c[0]=='DELETE' for c in client.calls))
 def test_client_rejects_other_destinations_without_sending_token(self):
  with self.assertRaises(PublishError):Client('fixture-token').call('GET','https://example.invalid/')
 def test_redirects_are_not_followed(self):self.assertIsNone(NoRedirect().redirect_request(None,None,None,None,None,None))
 def test_http_errors_do_not_echo_credentials(self):
  client=Client('FixtureSensitiveToken')
  with patch.object(client.opener,'open',side_effect=urllib.error.HTTPError(BASE+'/edits',403,'FixtureSensitiveToken',{},None)):
   with self.assertRaises(PublishError) as error:client.call('POST',BASE+'/edits',{})
  self.assertNotIn('FixtureSensitiveToken',str(error.exception));self.assertIn('403',str(error.exception))

if __name__=='__main__':unittest.main()
