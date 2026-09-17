#!/usr/bin/env python3
"""Verify and package the checked-in compact catalog. No network access is used."""
import gzip
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / 'data/poe.lock.json'
source = ROOT / 'data/poe/compact'
lock = json.loads(LOCK.read_text())

def digest(content):
    return hashlib.sha256(content).hexdigest()

def load(name, expected):
    path = source / name
    content = path.read_bytes()
    if digest(content) == expected:
        return content
    # Git with core.autocrlf=true rewrites newlines on checkout, which changes every hash.
    # The repair is provably lossless: it is applied only when it reproduces the pinned bytes.
    repaired = content.replace(b'\r\n', b'\n')
    if digest(repaired) == expected:
        path.write_bytes(repaired)
        print(f'Restored LF newlines in {name}; this checkout had converted them to CRLF')
        return repaired
    raise SystemExit(
        f'Checksum mismatch: {name}\n'
        f'  pinned sha256 {expected}\n'
        f'  actual sha256 {digest(content)}\n'
        '  Restore the pinned catalog with "git checkout -- data/poe", or, if the catalog was\n'
        '  edited on purpose, re-pin it with "python3 scripts/prepare_poe.py --update-lock".')

if '--update-lock' in sys.argv:
    for name in lock['files']:
        content = (source / name).read_bytes().replace(b'\r\n', b'\n')
        (source / name).write_bytes(content)
        lock['files'][name] = digest(content)
        lock['counts'][name] = len(json.loads(content))
    LOCK.write_text(json.dumps(lock, indent=2) + '\n')
    print('Re-pinned data/poe.lock.json:', lock['counts'])
    raise SystemExit(0)

out = ROOT / 'build/generated-poe/poe'
out.mkdir(parents=True, exist_ok=True)
for name, expected in lock['files'].items():
    content = load(name, expected)
    data = json.loads(content)
    if not isinstance(data, dict) or len(data) != lock['counts'][name]:
        raise SystemExit(f'Invalid catalog: {name}')
    (out / (name + '.gz')).write_bytes(gzip.compress(content, mtime=0))
(out / 'manifest.json').write_text(json.dumps(lock, indent=2) + '\n')
print('PoE compact catalog verified:', lock['profile'], lock['counts'])
