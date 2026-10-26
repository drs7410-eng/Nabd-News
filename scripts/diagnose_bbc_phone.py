"""Run only the exact BBC reference instrumentation test on a connected Android device.
The diagnostic APK has a separate package and does not read/change Nabd's personal data.
Requires Android platform-tools (adb) and Python 3. No API keys needed.
"""
import argparse
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default=shutil.which('adb') or 'adb')
parser.add_argument('--serial', help='Optional Android device serial if more than one is attached')
parser.add_argument('--directory', type=Path, default=Path(__file__).resolve().parent)
args = parser.parse_args()
base = args.directory
adb = [args.adb] + (['-s', args.serial] if args.serial else [])
package = 'com.nabd.news.bbcdiagnostic'

for filename in ['Nabd-BBC-diagnostic.apk', 'Nabd-BBC-diagnostic-tests.apk']:
    apk = base / filename
    if not apk.exists():
        raise SystemExit('Missing diagnostic APK: ' + str(apk))
    subprocess.run(adb + ['install', '-r', str(apk)], check=True)

result = subprocess.run(adb + ['shell', 'am', 'instrument', '-w', '-e', 'class',
    'com.nabd.news.BbcReferenceDeviceTest', '-e', 'bbcReference', 'true',
    package + '.test/androidx.test.runner.AndroidJUnitRunner'], capture_output=True)
(base / 'phone-instrumentation-result.txt').write_bytes(result.stdout + result.stderr)
for name in ['report.json', 'report-ar.md', 'bbc-article-response.html']:
    read = subprocess.run(adb + ['exec-out', 'run-as', package, 'cat', 'files/bbc-reference/' + name], capture_output=True)
    if read.returncode == 0:
        (base / ('phone-' + name)).write_bytes(read.stdout)
        print('Saved phone-' + name)
    else:
        print('Not available: ' + name)
print('Acceptance is established only when report.json says FULL_ARTICLE; inspect reasons and native/Room fields separately.')
print('This command never opens the publisher website or changes the production Nabd reader.')
if result.returncode != 0 or b'FAILURES!!!' in result.stdout or b'INSTRUMENTATION_FAILED' in result.stdout:
    raise SystemExit(1)
