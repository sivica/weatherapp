#!/usr/bin/env python3
"""Private runner materialization, signing and inspection. Never prints secrets."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as E
import zipfile

PACKAGE = 'ivica.weatherapplication'


def trusted_context(env):
    expected = {'GITHUB_ACTIONS':'true', 'GITHUB_REPOSITORY':'sivica/weatherapp',
                'GITHUB_REF':'refs/heads/main', 'GITHUB_EVENT_NAME':'workflow_dispatch',
                'GITHUB_ACTOR_ID':'61886', 'GITHUB_REPOSITORY_ID':'338999765',
                'GITHUB_REPOSITORY_OWNER_ID':'61886',
                'GITHUB_WORKFLOW_REF':'sivica/weatherapp/.github/workflows/internal-play.yml@refs/heads/main'}
    if any(env.get(k) != v for k,v in expected.items()):
        raise ValueError('Only the owner-dispatched main workflow can use private build inputs.')


def version_code(value):
    if not re.fullmatch(r'[1-9][0-9]{0,9}', value or '') or not 2 <= int(value) <= 2100000000:
        raise ValueError('Version code must be greater than the first uploaded version (1).')
    return int(value)


def publish_guard(env):
    trusted_context(env)
    version_code(env.get('CI_VERSION_CODE'))
    if env.get('UPLOAD_TO_INTERNAL') == 'true':
        if any(env.get(k) != 'true' for k in ('PLAY_PUBLISH_READY','PLAY_APP_BOOTSTRAPPED','OWNER_ONLY_TESTERS_CONFIRMED')):
            raise ValueError('Publishing is disabled until approved federation, Play bootstrap and owner-only testers are confirmed.')
        if not env.get('PLAY_WIF_PROVIDER') or not env.get('PLAY_SERVICE_ACCOUNT'):
            raise ValueError('Approved short-lived publishing identity is not configured.')


def write_private(path, value):
    fd = os.open(path, os.O_WRONLY|os.O_CREAT|os.O_EXCL, 0o600)
    with os.fdopen(fd,'wb') as stream:
        stream.write(value)


def materialize(env):
    trusted_context(env)
    root = Path(env['RUNNER_TEMP']).resolve()
    private = Path(tempfile.mkdtemp(prefix='weatherapp-private-', dir=root))
    private.chmod(0o700)
    try:
        data = base64.b64decode(env['WEATHERAPP_UPLOAD_KEYSTORE_B64'], validate=True)
        if not data or len(data)>65536: raise ValueError('Invalid upload-keystore input.')
        password = env['WEATHERAPP_UPLOAD_PASSWORD'].rstrip('\r\n')
        if not password or '\n' in password or '\r' in password: raise ValueError('Invalid signing-password input.')
        write_private(private/'upload.p12', data)
        write_private(private/'password.txt', password.encode())
        if env.get('INCLUDE_WEATHER_KEY') == 'true':
            key = env.get('WEATHERAPP_WEATHER_API_KEY','')
            if not re.fullmatch(r'[A-Za-z0-9]{16,128}',key): raise ValueError('Invalid weather-key input.')
            write_private(private/'weather-key.txt', key.encode())
        return private
    except BaseException:
        shutil.rmtree(private)
        raise


def checked(args):
    result = subprocess.run(args, capture_output=True, text=True, timeout=120)
    if result.returncode: raise RuntimeError('Private build verification command failed; sensitive diagnostics were withheld.')
    return result.stdout


def build(workspace, private, env):
    trusted_context(env)
    expected=version_code(env.get('CI_VERSION_CODE'))
    args=['./gradlew','--no-daemon','--no-build-cache','--no-configuration-cache',
          '-I','ci/internal.init.gradle','-PweatherappCiVersionCode='+str(expected)]
    if env.get('INCLUDE_WEATHER_KEY')=='true':
        args.append('-PweatherappCiKeyFile='+str(private/'weather-key.txt'))
    args.append(':app:bundleRelease')
    # Never send private compiler/Gradle diagnostics to the public Actions log.
    result=subprocess.run(args,cwd=workspace,env=env,capture_output=True,text=True,timeout=900)
    if result.returncode:raise RuntimeError('Private bundle build failed; diagnostics withheld.')
    print('Private internal bundle build completed.')


def bundletool(home, args):
    cache=home/'caches/modules-2/files-2.1'
    versions=list((cache/'com.android.tools.build/bundletool').glob('*/*/*.jar'))
    if not versions: raise RuntimeError('The build did not resolve its official bundletool dependency.')
    chosen=max(versions,key=lambda p:tuple(int(x) for x in p.parent.parent.name.split('.') if x.isdigit()))
    others=[p for p in cache.glob('*/*/*/*/*.jar') if p not in versions]
    cp=os.pathsep.join(str(p) for p in [chosen]+others)
    return checked([str(Path(os.environ['JAVA_HOME'])/'bin/java'),'-cp',cp,
                    'com.android.tools.build.bundletool.BundleToolMain']+args)


def sign_and_verify(workspace, private, env):
    trusted_context(env)
    expected=version_code(env.get('CI_VERSION_CODE'))
    public=json.loads((workspace/'ci/signing-public.json').read_text())
    source=E.parse(workspace/'app/src/main/res/values/strings.xml').getroot()
    if next(e for e in source if e.get('name')=='open_weather_api_key').text:
        raise ValueError('The tracked weather-key placeholder must remain empty.')
    java=Path(env['JAVA_HOME'])/'bin'
    store=private/'upload.p12'; password=private/'password.txt'
    certificate=private/'upload-certificate.der'
    checked([str(java/'keytool'),'-exportcert','-keystore',str(store),'-storepass:file',str(password),
             '-alias',public['alias'],'-file',str(certificate)])
    fingerprint=hashlib.sha256(certificate.read_bytes()).hexdigest().upper()
    if fingerprint != public['certificate_sha256']: raise ValueError('Upload certificate does not match the reviewed public fingerprint.')
    unsigned=workspace/'app/build/outputs/bundle/release/app-release.aab'; signed=private/'weatherapp-internal.aab'
    checked([str(java/'jarsigner'),'-keystore',str(store),'-storetype','PKCS12',
             '-storepass:file',str(password),'-keypass:file',str(password),'-sigalg','SHA256withRSA',
             '-digestalg','SHA-256','-signedjar',str(signed),str(unsigned),public['alias']])
    checked([str(java/'jarsigner'),'-verify','-strict','-keystore',str(store),'-storepass:file',str(password),str(signed)])
    signed.chmod(0o600)
    home=Path(env['GRADLE_USER_HOME'])
    bundletool(home,['validate','--bundle='+str(signed)])
    manifest=E.fromstring(bundletool(home,['dump','manifest','--bundle='+str(signed),'--module=base']))
    ns='{http://schemas.android.com/apk/res/android}'
    if manifest.get('package')!=PACKAGE or int(manifest.get(ns+'versionCode'))!=expected:
        raise ValueError('Bundle identity/version does not match the selected internal release.')
    if manifest.find('application').get(ns+'debuggable','false')!='false': raise ValueError('A debug bundle cannot be published.')
    if any('Fixture' in e.get(ns+'name','') for e in manifest.iter('activity')): raise ValueError('Fixture activity leaked into release.')
    resources=bundletool(home,['dump','resources','--bundle='+str(signed),'--values'])
    if not re.search(r'string/open_weather_api_key\n\s*\(default\) - \[STR\] ""',resources):
        raise ValueError('Compiled tracked key resource is not empty.')
    embedded=env.get('INCLUDE_WEATHER_KEY')=='true'
    if embedded:
        key=(private/'weather-key.txt').read_bytes()
        with zipfile.ZipFile(signed) as archive:
            if not any(key in archive.read(name) for name in archive.namelist()): raise ValueError('Selected weather key is absent from bundle.')
    else:
        fields=[p for p in (workspace/'app/build/generated').rglob('BuildConfig.java') if 'release' in p.parts and 'androidTest' not in str(p)]
        if len(fields)!=1 or not re.search(r'OPENWEATHER_API_KEY\s*=\s*""\s*;',fields[0].read_text()): raise ValueError('Key-free release field is not empty.')
    report={'package':PACKAGE,'versionCode':expected,'weatherKeyEmbedded':embedded,
            'certificateSha256':fingerprint,'sha256':hashlib.sha256(signed.read_bytes()).hexdigest()}
    write_private(private/'bundle-report.json',(json.dumps(report)+'\n').encode())
    print('Signed internal bundle verified: version '+str(expected)+'; embedded weather key: '+str(embedded).lower())
    print('AAB SHA-256: '+report['sha256'])


def cleanup(env):
    trusted_context(env)
    temporary=Path(env['RUNNER_TEMP']).resolve()
    value=env.get('WEATHERAPP_PRIVATE_DIR')
    if value:
        private=Path(value)
        if private.is_symlink() or private.parent.resolve()!=temporary or not private.name.startswith('weatherapp-private-'):
            raise ValueError('Refusing cleanup outside this private runner directory.')
        if private.exists():shutil.rmtree(private)
    workspace=Path(env['GITHUB_WORKSPACE']).resolve()
    for path in (workspace/'app/build',workspace/'build'):
        if path.is_symlink():raise ValueError('Refusing redirected build-output cleanup.')
        if path.exists():shutil.rmtree(path)
    home=Path(env['GRADLE_USER_HOME'])
    if home.parent.resolve()!=temporary or home.name!='weatherapp-gradle': raise ValueError('Refusing unrelated Gradle-cache cleanup.')
    if home.exists():shutil.rmtree(home)


def main():
    env=os.environ
    if sys.argv[1]=='guard': publish_guard(env)
    elif sys.argv[1]=='materialize':
        private=materialize(env)
        with open(env['GITHUB_ENV'],'a') as output:output.write('WEATHERAPP_PRIVATE_DIR='+str(private)+'\n')
    elif sys.argv[1]=='build': build(Path(env['GITHUB_WORKSPACE']),Path(env['WEATHERAPP_PRIVATE_DIR']),env)
    elif sys.argv[1]=='sign': sign_and_verify(Path(env['GITHUB_WORKSPACE']),Path(env['WEATHERAPP_PRIVATE_DIR']),env)
    elif sys.argv[1]=='cleanup': cleanup(env)
    else:raise ValueError('Unknown private-build operation.')

if __name__=='__main__':
    try:main()
    except Exception:
        print('Private internal build stopped. No secret values or raw diagnostics were displayed.',file=sys.stderr)
        sys.exit(1)
