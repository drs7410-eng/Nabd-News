"""Verify the user-supplied Galaxy evidence used by BBC regression tests. No publisher/network requests."""
from pathlib import Path
import hashlib
import json
root = Path(__file__).resolve().parents[1]
expected = 'eb863a857895d7cf1724a7c69e724242170608c94875b364f1229cc3ddf9e18a'
files = [root / 'app/src/test/resources/bbc-galaxy/c6ly0gdxd5jlo.html', root / 'app/src/androidTest/assets/bbc-galaxy/c6ly0gdxd5jlo.html']
for path in files:
    assert hashlib.sha256(path.read_bytes()).hexdigest() == expected, 'Galaxy reference hash mismatch: ' + str(path)
blocks = json.loads((root / 'app/src/test/resources/bbc-galaxy/expected-blocks.json').read_text())
assert sum(b['type'] == 'paragraph' for b in blocks) == 36
assert sum(b['type'] == 'heading' for b in blocks) == 1
assert sum(b['type'] == 'image' for b in blocks) == 2
assert len('\n\n'.join(b['text'] for b in blocks if b['type'] != 'image')) == 5373
print('Galaxy HTML digest and independently audited expected blocks verified.')
