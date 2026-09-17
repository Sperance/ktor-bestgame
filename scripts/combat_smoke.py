"""Real HTTP + Mongo combat: ownership, versions, idempotency, progression and loot."""
import uuid
from concurrent.futures import ThreadPoolExecutor

def run(request, token, other_token, cid):
    root = '/api/v1/combat/characters/' + cid
    world = request('GET', '/api/v1/combat/catalog', token=token)['data']
    assert len(world['zones']) == 3 and len(world['lootTables']) == 3
    request('GET', root, token=other_token, expected=404)
    state = request('GET', root, token=token)['data']
    def start(boss=False, **overrides):
        command = dict(expectedVersion=state['characterVersion'], requestId=uuid.uuid4().hex, zoneId='coast', boss=boss)
        command.update(overrides)
        return command
    request('POST', root + '/start', start(True), token, 400)
    request('POST', root + '/start', start(zoneId='citadel'), token, 400)
    request('POST', root + '/start', start(), other_token, 404)
    # Concurrent starts on the same character must produce one battle.
    commands = [start(), start()]
    def compete(command):
        try: return request('POST', root + '/start', command, token)['data']
        except AssertionError as e:
            assert e.args[0][2] == 409, e
            return None
    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(compete, commands))
    assert sum(r is not None for r in results) == 1
    state = next(r for r in results if r)
    successful = commands[next(i for i, r in enumerate(results) if r)]
    assert request('POST', root + '/start', successful, token)['data'] == state
    request('POST', root + '/start', dict(successful, boss=True), token, 400)
    request('POST', root + '/start', start(), token, 400)
    initial_hp = state['battle']['hero']['life']
    last_command = None
    # Three ordinary victories unlock the boss, then one boss victory gives two rolls.
    for encounter in range(4):
        if encounter:
            state = request('POST', root + '/start', start(encounter == 3), token)['data']
        for _ in range(80):
            battle = state['battle']
            if battle['status'] != 'ACTIVE': break
            action = 'POTION' if battle['hero']['life'] < battle['hero']['maxLife'] * .65 and battle['potions'] else ('POWER' if battle['hero']['mana'] >= 8 else 'ATTACK')
            last_command = dict(expectedVersion=state['characterVersion'], requestId=uuid.uuid4().hex, battleId=battle['id'], action=action)
            next_state = request('POST', root + '/act', last_command, token)['data']
            assert request('POST', root + '/act', last_command, token)['data'] == next_state
            request('POST', root + '/act', dict(last_command, requestId=uuid.uuid4().hex), token, 409)
            state = next_state
        assert state['battle']['status'] == 'VICTORY', state['battle']
        assert state['battle']['rewards'][0]['name'] == 'Опыт'
        assert request('GET', root, token=token)['data'] == state
    assert state['zoneKills']['coast'] == 0
    before = request('GET', '/api/v1/character/' + cid + '/equipment', token=token)['data']
    assert request('POST', root + '/act', last_command, token)['data'] == state
    assert request('GET', '/api/v1/character/' + cid + '/equipment', token=token)['data'] == before
    request('PUT', '/api/v1/character?id=' + cid, {'expectedVersion': state['characterVersion'], 'changes': {'battle': None, 'zoneKills': {'coast': 100}}}, token, 400)
    state = request('POST', root + '/start', start(), token)['data']
    flee = dict(expectedVersion=state['characterVersion'], requestId=uuid.uuid4().hex, battleId=state['battle']['id'], action='FLEE')
    fled = request('POST', root + '/act', flee, token)['data']
    assert fled['battle']['status'] == 'FLED' and not fled['battle']['rewards']
    print('Combat HTTP checks passed: four victories, boss loot, ownership, CAS, duplicate commands, escape')
