#!/usr/bin/env python3
"""Verify and package the checked-in compact catalog. No network access is used."""
import gzip
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
lock = json.loads((ROOT / 'data/poe.lock.json').read_text())
source = ROOT / 'data/poe/compact'
out = ROOT / 'build/generated-poe/poe'
out.mkdir(parents=True, exist_ok=True)
for name, expected in lock['files'].items():
    content = (source / name).read_bytes()
    if hashlib.sha256(content).hexdigest() != expected:
        raise SystemExit(f'Checksum mismatch: {name}; review catalog and update lock')
    data = json.loads(content)
    if not isinstance(data, dict) or len(data) != lock['counts'][name]:
        raise SystemExit(f'Invalid catalog: {name}')
    (out / (name + '.gz')).write_bytes(gzip.compress(content, mtime=0))
(out / 'manifest.json').write_text(json.dumps(lock, indent=2) + '\n')
print('PoE compact catalog verified:', lock['profile'], lock['counts'])
