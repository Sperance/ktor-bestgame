#!/usr/bin/env python3
"""Reproducible build-time import. Server startup never downloads mutable game data."""
import argparse, gzip, hashlib, json, pathlib, urllib.request
ROOT = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--source-dir', type=pathlib.Path)
args = parser.parse_args()
lock = json.loads((ROOT / 'data/poe.lock.json').read_text())
out = ROOT / 'build/generated-poe/poe'
out.mkdir(parents=True, exist_ok=True)
for name, expected in lock['files'].items():
    target = out / (name + '.gz')
    if target.exists() and hashlib.sha256(gzip.decompress(target.read_bytes())).hexdigest() == expected:
        continue
    if args.source_dir:
        content = (args.source_dir / name).read_bytes()
    else:
        url = f"https://raw.githubusercontent.com/{lock['repository']}/{lock['commit']}/data/{name}"
        with urllib.request.urlopen(url, timeout=120) as response:
            content = response.read()
    if hashlib.sha256(content).hexdigest() != expected:
        raise SystemExit(f'Checksum mismatch: {name}; refusing unreviewed game data')
    data = json.loads(content)
    if not isinstance(data, dict) or not data:
        raise SystemExit(f'Invalid catalog: {name}')
    target.write_bytes(gzip.compress(content, mtime=0))
(out / 'manifest.json').write_text(json.dumps(lock, ensure_ascii=False, indent=2) + '\n')
print('PoE catalog verified:', lock['version'], lock['counts'])
