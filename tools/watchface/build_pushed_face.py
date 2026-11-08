#!/usr/bin/env python3
"""Packages the Noor watch faces for Watch Face Push (0.9.8) and validates each with Google's validator.

The watch app carries several versions of one watch face (package <watch app id>.watchfacepush.noor), all with the
same package name, so that the watch app can replace the one in the user's slot with another in place (the face
stays active, the system keeps the user's settings for it):

- "original": the v1.3.2 face, resources unchanged (legacy/watchface-v1.3.2 is not modified: they are copied into
  a build folder and linked under the Watch Face Push package name).
- "<design>_<colour>": the Noor face of 0.9.8 (tools/watchface/noor_face.py), three designs in five colours. They
  show Noor's own prayer data through Noor's complications.

Each APK is signed with the watch-face development key (public on purpose, signing/) and validated; the validation
tokens, the ids and the build number are written as Android resources for the watch app, the APKs as its assets.

usage: build_pushed_face.py <sdk_dir> <out_dir> <validator_cli_jar>
"""
import concurrent.futures
import os
import re
import shutil
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import noor_face  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
LEGACY_RES = os.path.join(ROOT, 'legacy', 'watchface-v1.3.2', 'NoorWatchFace', 'watchface', 'src', 'main', 'res')
APP_ID = 'com.noor.smartwatch'
FACE_PACKAGE = APP_ID + '.watchfacepush.noor'
# One build number for every face in this build: the watch app replaces an installed face whose build is older.
# 1 was 0.9.6-0.9.7 (the original face only).
FACE_BUILD = 2
KEYSTORE = os.path.join(ROOT, 'signing', 'noor-watchface-dev.keystore')
VARIANTS = ['original'] + [f'{d}_{c}' for d in noor_face.DESIGNS for c in noor_face.COLOURS]
DEFAULT = 'horizon_gold'


def manifest(variant):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{FACE_PACKAGE}">
    <uses-feature android:name="android.hardware.type.watch" />
    <application android:label="@string/watch_face_name" android:hasCode="false">
        <meta-data android:name="com.google.android.wearable.standalone" android:value="true" />
        <meta-data android:name="noor.face.variant" android:value="{variant}" />
        <meta-data android:name="noor.face.build" android:value="{FACE_BUILD}" />
        <property android:name="com.google.wear.watchface.format.version" android:value="4" />
    </application>
</manifest>
'''


def run(cmd, **kw):
    r = subprocess.run(cmd, capture_output=True, text=True, **kw)
    if r.returncode != 0:
        sys.stderr.write(r.stdout + r.stderr)
        raise SystemExit(f'failed: {cmd[0]}')
    return r.stdout + r.stderr


def build_tools(sdk):
    versions = sorted(os.listdir(os.path.join(sdk, 'build-tools')), key=lambda v: [int(x) if x.isdigit() else 0 for x in re.split(r'[.-]', v)])
    return os.path.join(sdk, 'build-tools', versions[-1])


def build_one(variant, bt, platform, assets, validator):
    with tempfile.TemporaryDirectory() as tmp:
        res = os.path.join(tmp, 'res')
        if variant == 'original':
            shutil.copytree(LEGACY_RES, res)
        else:
            design, colour = variant.split('_')
            noor_face.write_variant(res, design, colour)
        man = os.path.join(tmp, 'AndroidManifest.xml'); open(man, 'w').write(manifest(variant))
        run([os.path.join(bt, 'aapt2'), 'compile', '--dir', res, '-o', os.path.join(tmp, 'res.zip')])
        run([os.path.join(bt, 'aapt2'), 'link', '-o', os.path.join(tmp, 'unaligned.apk'), '-I', platform, '--manifest', man,
             '--min-sdk-version', '36', '--target-sdk-version', '36', '--version-code', str(FACE_BUILD),
             '--version-name', f'0.9.8-{variant}', '--no-auto-version', os.path.join(tmp, 'res.zip')])
        run([os.path.join(bt, 'zipalign'), '-p', '-f', '4', os.path.join(tmp, 'unaligned.apk'), os.path.join(tmp, 'aligned.apk')])
        apk = os.path.join(assets, f'{variant}.apk')
        run([os.path.join(bt, 'apksigner'), 'sign', '--ks', KEYSTORE, '--ks-key-alias', 'noor-wf-dev', '--ks-pass', 'pass:noor-dev-key',
             '--key-pass', 'pass:noor-dev-key', '--v1-signing-enabled', 'false', '--v2-signing-enabled', 'true',
             '--v3-signing-enabled', 'true', '--v4-signing-enabled', 'false', '--out', apk, os.path.join(tmp, 'aligned.apk')])
    report = run(['java', '-jar', validator, f'--apk_path={apk}', f'--package_name={APP_ID}'])
    m = re.search(r'[Tt]oken[^:\n]*:\s*(\S+)', report)
    if not m:
        sys.stderr.write(report)
        raise SystemExit(f'{variant}: validator produced no token')
    return variant, m.group(1), os.path.getsize(apk), report


def main(sdk, out, validator):
    bt = build_tools(sdk)
    platforms = [p for p in os.listdir(os.path.join(sdk, 'platforms')) if re.fullmatch(r'android-\d+', p) and int(p[8:]) >= 33]
    if not platforms:
        raise SystemExit('needs an Android platform 33 or newer in ' + sdk)
    platform = os.path.join(sdk, 'platforms', 'android-36' if 'android-36' in platforms else max(platforms, key=lambda p: int(p[8:])), 'android.jar')
    assets = os.path.join(out, 'assets', 'faces'); res_out = os.path.join(out, 'res', 'values')
    os.makedirs(assets, exist_ok=True); os.makedirs(res_out, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        results = list(pool.map(lambda v: build_one(v, bt, platform, assets, validator), VARIANTS))
    tokens = {v: t for v, t, _, _ in results}
    # The face Wear OS adds to the watch's faces by itself when the watch app is installed (assets/default_watchface.apk
    # and its token in the manifest): the Horizon face in gold, the default the watch app also chooses.
    shutil.copy(os.path.join(assets, f'{DEFAULT}.apk'), os.path.join(out, 'assets', 'default_watchface.apk'))
    open(os.path.join(out, 'validation.txt'), 'w').write('\n\n'.join(f'== {v}\n{r}' for v, _, _, r in results))
    items = lambda xs: ''.join(f'        <item>{x}</item>\n' for x in xs)
    open(os.path.join(res_out, 'noor_face.xml'), 'w').write(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by tools/watchface/build_pushed_face.py -->\n'
        '<resources xmlns:tools="http://schemas.android.com/tools" '
        'tools:keep="@string/default_wf_token,@array/noor_face_ids,@array/noor_face_tokens,@integer/noor_face_version_code">\n'
        f'    <string name="default_wf_token" translatable="false">{tokens[DEFAULT]}</string>\n'
        f'    <integer name="noor_face_version_code">{FACE_BUILD}</integer>\n'
        f'    <string-array name="noor_face_ids" translatable="false">\n{items(VARIANTS)}    </string-array>\n'
        f'    <string-array name="noor_face_tokens" translatable="false">\n{items(tokens[v] for v in VARIANTS)}    </string-array>\n'
        '</resources>\n')
    for v, _, size, _ in results:
        print(f'{FACE_PACKAGE} {v}: {size} bytes, validation passed')


if __name__ == '__main__':
    main(*sys.argv[1:4])
