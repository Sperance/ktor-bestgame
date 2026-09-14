"""Black-box regression checks against the packaged server, using only public HTTP requests."""
import json
import urllib.request
import urllib.error
import uuid
from concurrent.futures import ThreadPoolExecutor

def run(admin_password):
    suffix = uuid.uuid4().hex[:10]
    count = 0
    def request(method, path, data=None, token=None, expected=200):
        nonlocal count
        headers = {'Content-Type': 'application/json'}
        if token: headers['Authorization'] = 'Bearer ' + token
        req = urllib.request.Request('http://localhost:8080' + path, data=None if data is None else json.dumps(data).encode(), headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=20) as response: status, raw = response.status, response.read().decode()
        except urllib.error.HTTPError as e:
            status, raw = e.code, e.read().decode()
        assert status == expected, (method, path, status, expected, raw[:400])
        assert admin_password not in raw, 'Password leaked in response'
        count += 1
        return json.loads(raw) if raw else {}
    def login(name, password): return request('POST', '/api/v1/poe/token', {'login': name, 'password': password})['data']['token']
    admin = login('admin', admin_password)
    request('GET', '/api/v1/character', expected=401)
    request('GET', '/api/v1/user', expected=401)
    request('GET', '/system/routes', expected=401)
    users = []
    for n in range(2):
        name = 'api_' + suffix + str(n); password = 'Secret-pass-' + suffix + str(n)
        user = request('POST', '/api/v1/user', [{'name': name, 'login': name, 'email': name + '@example.test', 'password': password, 'age': 25}], admin)['data'][0]
        assert 'password' not in user and 'salt' not in user
        token = login(name, password)
        character = request('POST', '/api/v1/character', [{'name': 'hero_' + suffix + str(n)}], token)['data'][0]
        users.append((user, token, character, password, name))
    user, token, character, password, name = users[0]
    other, other_token, other_char, _, _ = users[1]
    cid = character['_id']; other_id = other_char['_id']
    assert {x['_id'] for x in request('GET', '/api/v1/character', token=token)['data']} == {cid}
    assert {x['id'] for x in request('GET', '/api/v1/user', token=token)['data']} == {user['id']}
    assert request('GET', '/api/v1/character/count', token=token)['data']['count'] == 1
    for path in ['/api/v1/character?id=' + other_id, '/api/v1/character/' + other_id + '/stats', '/api/v1/character/inventory/equipped?characterId=' + other_id, '/api/v1/user?id=' + other['id']]:
        request('GET', path, token=token, expected=404)
    request('GET', '/system/routes', token=token, expected=403)
    request('GET', '/api/v1/redemptioncodes', token=token, expected=403)
    request('PUT', '/api/v1/character?id=' + other_id, {'expectedVersion': 0, 'changes': {'name': 'stolen'}}, token, 404)
    request('DELETE', '/api/v1/character?id=' + other_id, {'expectedVersion': 0}, token, 404)
    request('PUT', '/api/v1/user?id=' + user['id'], {'expectedVersion': 1, 'changes': {'role': 'ADMIN'}}, token, 400)
    request('PUT', '/api/v1/character?id=' + cid, {'expectedVersion': 0, 'changes': {'money': 999999, 'userId': other['id']}}, token, 400)
    request('PUT', '/api/v1/character?id=' + cid, {'name': 'old-contract'}, token, 400)
    request('POST', '/api/v1/user', [{'name': 'illegal', 'role': 'ADMIN'}], token, 403)
    request('GET', '/api/v1/user/login?login=' + name + '&password=never-log-this', token=token, expected=404)
    request('GET', '/api/v1/user/changePassword?id=' + user['id'], token=token, expected=405)
    request('GET', '/api/v1/user/login/byDeviceId?deviceId=anything', token=token, expected=404)
    changed = request('PUT', '/api/v1/character?id=' + cid, {'expectedVersion': 0, 'changes': {'description': 'updated'}}, token)['data']
    assert changed['version'] == 1
    request('PUT', '/api/v1/character?id=' + cid, {'expectedVersion': 0, 'changes': {'description': 'stale'}}, token, 409)
    templates = request('GET', '/api/v1/equipment?size=100', token=admin)['data']
    ring = next(x for x in templates if x['name'] == 'Coral Ring')
    sword = next(x for x in templates if x['name'] == 'Rusted Sword')
    request('POST', '/api/v1/character/inventory/itemToInventory?characterId=' + cid, {'expectedVersion': 1, 'equipmentId': ring['_id']}, token, 403)
    view = request('POST', '/api/v1/character/inventory/itemToInventory?characterId=' + cid, {'expectedVersion': 1, 'equipmentId': ring['_id']}, admin)['data']
    uuid_item = view['inventory'][0]['uuid']; baseline = view['stats']['values']['maximum_life']
    before_preview = request('GET', '/api/v1/character/' + cid + '/equipment', token=token)['data']
    preview = request('POST', '/api/v1/character/' + cid + '/compareEquipment', {'expectedVersion': 2, 'equipmentUuid': uuid_item, 'slot': 'RING_LEFT'}, token)['data']
    assert preview['allowed'] and preview['after']['values']['maximum_life'] > preview['before']['values']['maximum_life']
    blocked = request('POST', '/api/v1/character/' + cid + '/compareEquipment', {'expectedVersion': 2, 'equipmentUuid': uuid_item, 'slot': 'HELMET'}, token)['data']
    assert not blocked['allowed']
    request('POST', '/api/v1/character/' + other_id + '/compareEquipment', {'expectedVersion': 0, 'equipmentUuid': uuid_item, 'slot': 'RING_LEFT'}, token, 404)
    options = request('GET', '/api/v1/character/' + cid + '/craftOptions?equipmentUuid=' + uuid_item, token=token)['data']
    assert all(not x['available'] for x in options['options'])
    assert request('GET', '/api/v1/character/' + cid + '/equipment', token=token)['data'] == before_preview
    filtered = request('GET', '/api/v1/equipment/paged?q=Coral&slot=RING&size=1', token=token)['data']
    assert filtered['totalItems'] == 1 and filtered['items'][0]['_id'] == ring['_id']
    assert request('GET', '/api/v1/character/paged?q=hero_', token=token)['data']['totalItems'] == 1
    request('GET', '/api/v1/equipment/paged?stat=$where&minStat=0', token=token, expected=400)
    view = request('POST', '/api/v1/character/' + cid + '/equip', {'expectedVersion': 2, 'equipmentUuid': uuid_item, 'slot': 'RING_LEFT'}, token)['data']
    assert view['stats']['values']['maximum_life'] > baseline
    request('POST', '/api/v1/character/' + cid + '/equip', {'expectedVersion': 2, 'equipmentUuid': uuid_item, 'slot': 'RING_RIGHT'}, token, 409)
    request('POST', '/api/v1/character/' + cid + '/equip', {'expectedVersion': 3, 'equipmentUuid': uuid_item, 'slot': 'HELMET'}, token, 400)
    request('POST', '/api/v1/character/' + other_id + '/equip', {'expectedVersion': 0, 'equipmentUuid': uuid_item, 'slot': 'RING_LEFT'}, token, 404)
    # Template editing is versioned and does not change the snapshotted instance.
    request('PUT', '/api/v1/equipment?id=' + ring['_id'], {'expectedVersion': ring['version'], 'changes': {'name': 'Edited ' + suffix}}, token, 403)
    request('PUT', '/api/v1/equipment?id=' + ring['_id'], {'expectedVersion': ring['version'], 'changes': {'name': 'Edited ' + suffix}}, admin)
    request('PUT', '/api/v1/equipment?id=' + ring['_id'], {'expectedVersion': ring['version'], 'changes': {'name': 'stale'}}, admin, 409)
    stored = request('GET', '/api/v1/character/' + cid + '/equipment', token=token)['data']
    assert stored['inventory'][0]['baseSnapshot']['name'] == 'Coral Ring'
    view = request('POST', '/api/v1/character/' + cid + '/unequip', {'expectedVersion': 3, 'slot': 'RING_LEFT'}, token)['data']
    assert view['stats']['values']['maximum_life'] == baseline
    # Deleting and recreating characters releases the per-account character limit.
    request('DELETE', '/api/v1/character?id=' + cid, {'expectedVersion': 3}, token, 409)
    request('DELETE', '/api/v1/character?id=' + cid, {'expectedVersion': 4}, token)
    request('GET', '/api/v1/character?id=' + cid, token=token, expected=404)
    profile = request('GET', '/api/v1/user?id=' + user['id'], token=token)['data']
    assert profile['countCharacters'] == 0
    # Revocation is checked against MongoDB on every authenticated request.
    new_password = password + '-new'
    request('POST', '/api/v1/user/changePassword', {'expectedVersion': profile['version'], 'currentPassword': password, 'newPassword': new_password}, token)
    request('GET', '/api/v1/user', token=token, expected=401)
    request('POST', '/api/v1/poe/token', {'login': name, 'password': password}, expected=401)
    renewed = login(name, new_password)
    profile = request('GET', '/api/v1/user?id=' + user['id'], token=renewed)['data']
    request('POST', '/api/v1/user/changeRole?id=' + user['id'], {'expectedVersion': profile['version'], 'role': 'ADMIN'}, renewed, 403)
    request('POST', '/api/v1/user/changeRole?id=' + user['id'], {'expectedVersion': profile['version'], 'role': 'ADMIN'}, admin)
    request('GET', '/api/v1/user', token=renewed, expected=401)
    # Concurrent writes must not silently overwrite each other.
    def compete(n):
        try:
            request('PUT', '/api/v1/character?id=' + other_id, {'expectedVersion': 0, 'changes': {'description': 'race-' + str(n)}}, other_token)
            return 200
        except AssertionError as error:
            assert error.args[0][2] == 409, error
            return 409
    with ThreadPoolExecutor(max_workers=2) as pool:
        assert sorted(pool.map(compete, range(2))) == [200, 409]
    currency = request('GET', '/api/v1/items', token=admin)['data'][0]
    item_id = currency['_id']
    code = request('POST', '/api/v1/redemptioncodes', [{'code': 'reward-' + suffix, 'treasure': [{'itemId': item_id, 'amount': 3}]}], admin)['data'][0]
    view = request('POST', '/api/v1/character/' + other_id + '/redeem', {'expectedVersion': 1, 'code': code['code']}, other_token)['data']
    assert next(x['amount'] for x in view['items'] if x['itemId'] == item_id) == 3
    request('POST', '/api/v1/character/' + other_id + '/redeem', {'expectedVersion': 2, 'code': code['code']}, other_token, 400)
    recipe = request('POST', '/api/v1/recipe', [{'name': 'recipe-' + suffix, 'timeWork': 0, 'arrayIn': [{'itemId': item_id, 'amount': 2}], 'arrayOut': [{'itemId': item_id, 'amount': 1}]}], admin)['data'][0]
    command = {'expectedVersion': 2, 'recipeId': recipe['_id'], 'recipeVersion': recipe['version'], 'ingredientIds': [item_id], 'amount': 1}
    view = request('POST', '/api/v1/character/' + other_id + '/useRecipe', command, other_token)['data']
    assert next(x['amount'] for x in view['items'] if x['itemId'] == item_id) == 2
    request('POST', '/api/v1/character/' + other_id + '/useRecipe', dict(command, expectedVersion=3), other_token, 409)
    request('POST', '/api/v1/character/' + other_id + '/useRecipe', dict(command, expectedVersion=3, recipeVersion=1, amount=2), other_token, 400)
    unchanged = request('GET', '/api/v1/character/' + other_id + '/equipment', token=other_token)['data']
    assert unchanged['characterVersion'] == 3
    assert next(x['amount'] for x in unchanged['items'] if x['itemId'] == item_id) == 2
    # Malformed password requests cannot echo submitted secret values in decoder errors.
    secret = 'should-never-appear-in-errors'
    result = request('POST', '/api/v1/poe/token', {'login': name, 'password': {'secret': secret}}, expected=400)
    assert secret not in json.dumps(result)
    from combat_smoke import run as combat_checks
    combat_checks(request, other_token, admin, other_id)
    from passives_smoke import run as passive_checks
    passive_checks(request, other_token, admin, other_id)
    print('HTTP security and equipment checks passed:', count)
