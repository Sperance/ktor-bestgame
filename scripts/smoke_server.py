#!/usr/bin/env python3
"""Start the actual distribution twice against the CI-only database and inspect HTTP responses."""
import json
import os
from pathlib import Path
import subprocess
import time
import secrets
from security_smoke import run as security_checks
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

def raw(path, headers=None):
    request = urllib.request.Request('http://localhost:8080' + path, headers=headers or {})
    with urllib.request.urlopen(request, timeout=5) as response:
        return response.status, dict(response.headers), response.read().decode()

def icon_checks(capabilities):
    """The drawn set is part of the contract: it is public, complete and cacheable."""
    assert capabilities['icons'] and capabilities['iconsEndpoint'] == '/api/v1/icons'
    manifest = get('/api/v1/icons')
    assert manifest['version'] == capabilities['iconSetVersion']
    assert manifest['total'] == capabilities['iconCount'] == len(manifest['icons'])
    bindings = get('/api/v1/icons/bindings')
    known = {icon['id'] for icon in manifest['icons']}
    for table in ['stats', 'weapons', 'slots', 'currencies', 'rarities', 'modifiers', 'bases']:
        assert bindings[table], table
        assert set(bindings[table].values()) <= known, table
    assert len(bindings['modifiers']) == lock['counts']['mods.json']
    assert len(bindings['bases']) == lock['counts']['base_items.json']

    status, headers, body = raw('/api/v1/icons/weapon-sword.svg')
    assert status == 200 and body.startswith('<svg') and 'image/svg+xml' in headers['Content-Type']
    assert 'max-age' in headers['Cache-Control']
    try:
        raw('/api/v1/icons/weapon-sword.svg', {'If-None-Match': headers['ETag']})
        raise AssertionError('Expected 304 for a matching ETag')
    except urllib.error.HTTPError as error:
        assert error.code == 304, error.code
    assert raw('/api/v1/icons/sprite.svg')[2].count('<symbol') == manifest['total']
    assert get('/api/v1/poe/catalog?type=bases&size=1')['items'][0]['icon'] in known
    assert set(get('/api/v1/poe/modifier-definitions?size=5')['icons'].values()) <= known
    assert all(option['icon'] in known for option in get('/api/v1/poe/currencies'))

admin_password = secrets.token_urlsafe(24)
server_env = dict(os.environ, SEED_DEMO_DATA="true", SEED_ADMIN_PASSWORD=admin_password)
for attempt in range(2):
    with (ROOT / f'build/startup-{attempt}.log').open('w') as log:
        process = subprocess.Popen([str(ROOT / 'build/install/ktor-bestgame/bin/ktor-bestgame')], cwd=ROOT, env=server_env, stdout=log, stderr=subprocess.STDOUT)
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
            icon_checks(capabilities)
            get('/system/health')
            if attempt == 0: security_checks(admin_password)
            print(f'Startup {attempt + 1}: catalog, MongoDB definitions, currencies, icons and health passed')
        finally:
            process.terminate()
            try:
                process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
