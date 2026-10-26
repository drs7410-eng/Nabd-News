"""Download pinned, unmodified real publisher snapshots from Mozilla's parser test corpus.
Test-only: never included in the APK or shown as live news. Requires no publisher requests.
"""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request

parser=argparse.ArgumentParser()
parser.add_argument('--offline',action='store_true',help='Verify committed fixture bytes without downloading or repairing')
args=parser.parse_args()
root = Path(__file__).resolve().parents[1]
for record in json.loads((root / 'scripts/reference-sources.json').read_text()):
    target = root / 'app/src/test/resources/reference' / record['id'] / 'source.html'
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != record['sha256']:
        if args.offline: raise RuntimeError('Missing/corrupted committed fixture: '+record['id'])
        data = urllib.request.urlopen(record['fixtureUrl'], timeout=40).read(4_000_001)
        if hashlib.sha256(data).hexdigest() != record['sha256']:
            raise RuntimeError('Fixture integrity mismatch: ' + record['id'])
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    print('Verified real archived fixture:', record['id'])

for record in json.loads((root / 'scripts/reference-sources.json').read_text()):
    target = root / 'app/src/test/resources/reference' / record['id'] / 'expected.html'
    if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest() != record['expectedSha256']:
        if args.offline: raise RuntimeError('Missing/corrupted committed expected fixture: '+record['id'])
        data = urllib.request.urlopen(record['expectedFixtureUrl'], timeout=40).read(4_000_001)
        if hashlib.sha256(data).hexdigest() != record['expectedSha256']:
            raise RuntimeError('Expected fixture integrity mismatch: ' + record['id'])
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
