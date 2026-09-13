#!/usr/bin/env python3
"""Start the actual distribution twice against the CI-only database and inspect HTTP responses."""
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
assert os.environ.get('MONGO_DB') == 'poe_startup_test', 'Use the isolated startup test database'
lock = json.loads((ROOT / 'data/poe.lock.json').read_text())

def get(path):
    with urllib.request.urlopen('http://localhost:8080' + path, timeout=5) as response:
        body = json.load(response)
        assert body['success'], body
        return body['data']

for attempt in range(2):
    with (ROOT / f'build/startup-{attempt}.log').open('w') as log:
        process = subprocess.Popen([str(ROOT / 'build/install/ktor-bestgame/bin/ktor-bestgame')], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        try:
            deadline = time.monotonic() + 90
            while True:
                if process.poll() is not None:
                    raise RuntimeError(f'Server exited: inspect build/startup-{attempt}.log')
                try:
                    capabilities = get('/api/v1/poe/capabilities')
                    break
                except (urllib.error.URLError, TimeoutError):
                    if time.monotonic() > deadline:
                        raise
                    time.sleep(0.5)
            assert capabilities['profile'] == 'compact-v1'
            assert capabilities['baseRecords'] == lock['counts']['base_items.json']
            assert capabilities['modifierRecords'] == lock['counts']['mods.json']
            assert len(get('/api/v1/poe/currencies')) == 13
            assert get('/api/v1/poe/catalog?type=bases')['total'] == 50
            assert get('/api/v1/poe/modifier-definitions')['total'] == 234
            get('/system/health')
            print(f'Startup {attempt + 1}: catalog, MongoDB definitions, currencies and health passed')
        finally:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
