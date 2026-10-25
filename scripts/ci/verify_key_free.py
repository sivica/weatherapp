#!/usr/bin/env python3
"""Verify the ordinary unsigned CI release, without needing a credential."""
import os
from pathlib import Path
import re
import xml.etree.ElementTree as E
from private_build import PACKAGE, bundletool


def verify(workspace, home):
    if (workspace/'debug-weather.properties').exists():
        raise ValueError('Key-free CI must not contain a local weather configuration.')
    source=E.parse(workspace/'app/src/main/res/values/strings.xml').getroot()
    if next(e for e in source if e.get('name')=='open_weather_api_key').text:
        raise ValueError('The tracked placeholder must be empty.')
    fields=[p for p in (workspace/'app/build/generated').rglob('BuildConfig.java') if 'release' in p.parts and 'androidTest' not in str(p)]
    if len(fields)!=1 or not re.search(r'OPENWEATHER_API_KEY\s*=\s*""\s*;',fields[0].read_text()):
        raise ValueError('The ordinary release field must be empty.')
    bundle=workspace/'app/build/outputs/bundle/release/app-release.aab'
    bundletool(home,['validate','--bundle='+str(bundle)])
    manifest=E.fromstring(bundletool(home,['dump','manifest','--bundle='+str(bundle),'--module=base']))
    ns='{http://schemas.android.com/apk/res/android}'
    if manifest.get('package')!=PACKAGE or manifest.find('application').get(ns+'debuggable','false')!='false':
        raise ValueError('Unexpected release identity or debuggable state.')
    if any('Fixture' in e.get(ns+'name','') for e in manifest.iter('activity')):
        raise ValueError('Fixture screens must not enter a release.')
    resources=bundletool(home,['dump','resources','--bundle='+str(bundle),'--values'])
    if not re.search(r'string/open_weather_api_key\n\s*\(default\) - \[STR\] ""',resources):
        raise ValueError('The compiled placeholder must be empty.')
    print('Ordinary release verified: empty weather key, correct package, nondebuggable, no fixture activities.')


if __name__=='__main__':
    verify(Path.cwd(),Path(os.environ['GRADLE_USER_HOME']))
