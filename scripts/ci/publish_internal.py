#!/usr/bin/env python3
"""Fixed-package, fixed-internal-track publisher using an ephemeral OAuth token."""
import json
import os
import re
from pathlib import Path
import sys
import urllib.error
import urllib.request
from private_build import PACKAGE, publish_guard, version_code

BASE='https://androidpublisher.googleapis.com/androidpublisher/v3/applications/'+PACKAGE
UPLOAD='https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/'+PACKAGE

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None

class PublishError(Exception):pass

class Client:
    def __init__(self,token):
        if not token:raise PublishError('Missing short-lived publishing token.')
        self.token=token;self.opener=urllib.request.build_opener(NoRedirect())
    def call(self,method,url,payload=None,binary=None):
        if not (url.startswith(BASE+'/') or url.startswith(UPLOAD+'/')):raise PublishError('Refusing an unexpected publishing destination.')
        headers={'Authorization':'Bearer '+self.token}
        if binary is not None:body=binary;headers['Content-Type']='application/octet-stream'
        else:body=None if payload is None else json.dumps(payload).encode();headers['Content-Type']='application/json'
        request=urllib.request.Request(url,data=body,headers=headers,method=method)
        try:
            with self.opener.open(request,timeout=120) as response:
                raw=response.read();return json.loads(raw) if raw else {}
        except urllib.error.HTTPError as error:
            raise PublishError('Google Play request rejected (HTTP '+str(error.code)+'); no raw request/response was logged.') from None
        except (TimeoutError,urllib.error.URLError):
            raise PublishError('Google Play request outcome is uncertain; inspect the internal track before retrying.') from None


def publish(client,bundle,report):
    if report.get('package')!=PACKAGE:raise PublishError('Unexpected app package.')
    code=version_code(str(report['versionCode']));edit=None;committing=False;committed=False
    try:
        edit=edit_id(client.call('POST',BASE+'/edits',{})['id'])
        current=client.call('GET',BASE+'/edits/'+edit+'/bundles')
        tracks=client.call('GET',BASE+'/edits/'+edit+'/tracks')
        used=[int(b['versionCode']) for b in current.get('bundles',[])]
        used += [int(c) for t in tracks.get('tracks',[]) for r in t.get('releases',[]) for c in r.get('versionCodes',[])]
        if any(previous>=code for previous in used):
            raise PublishError('Choose a new version code; existing versions are never replaced.')
        uploaded=client.call('POST',UPLOAD+'/edits/'+edit+'/bundles?uploadType=media',binary=bundle.read_bytes())
        if int(uploaded['versionCode'])!=code:raise PublishError('Play returned an unexpected bundle version.')
        # Track and status are constants, never user input. No listing/tester/account APIs are called.
        track={'track':'internal','releases':[{'versionCodes':[str(code)],'status':'completed','name':'Vedro internal '+str(code)}]}
        client.call('PUT',BASE+'/edits/'+edit+'/tracks/internal',track)
        client.call('POST',BASE+'/edits/'+edit+':validate',{})
        committing=True
        client.call('POST',BASE+'/edits/'+edit+':commit',{})
        committed=True
        print('Play acknowledged internal-track commit for version '+str(code)+'.')
    finally:
        if edit and not committing and not committed:
            try:client.call('DELETE',BASE+'/edits/'+edit)
            except PublishError:print('Draft edit cleanup could not be verified; inspect it before another upload.',file=sys.stderr)
    # A new edit provides a view of committed state; delete this readback edit afterward.
    verification=None
    try:
        verification=edit_id(client.call('POST',BASE+'/edits',{})['id'])
        state=client.call('GET',BASE+'/edits/'+verification+'/tracks/internal')
        if not any(str(code) in r.get('versionCodes',[]) and r.get('status')=='completed' for r in state.get('releases',[])):
            raise PublishError('Commit was acknowledged but internal-track readback differs; do not upload again blindly.')
        print('Verified: version '+str(code)+' is completed on the internal track.')
    finally:
        if verification:client.call('DELETE',BASE+'/edits/'+verification)


def edit_id(value):
    if not isinstance(value,str) or not re.fullmatch(r'[A-Za-z0-9_-]+',value):
        raise PublishError('Unexpected edit identifier; no follow-up request was made.')
    return value


def main():
    publish_guard(os.environ)
    if os.environ.get('UPLOAD_TO_INTERNAL')!='true':raise PublishError('Explicit internal-upload selection is required.')
    root=Path(os.environ['WEATHERAPP_PRIVATE_DIR'])
    report=json.loads((root/'bundle-report.json').read_text())
    import hashlib
    bundle=root/'weatherapp-internal.aab'
    if hashlib.sha256(bundle.read_bytes()).hexdigest()!=report['sha256']:raise PublishError('Bundle changed after signature validation.')
    publish(Client(os.environ.get('PLAY_ACCESS_TOKEN','')),bundle,report)

if __name__=='__main__':
    try:main()
    except PublishError as error:print(str(error),file=sys.stderr);sys.exit(1)
    except Exception:print('Internal publishing stopped; inspect Play state before retrying. Sensitive diagnostics were withheld.',file=sys.stderr);sys.exit(1)
