"""Authenticated passive tree checks after combat earned a level for the fixture."""
import uuid

def run(request, token, other_token, cid):
    root = '/api/v1/passives/characters/' + cid
    tree = request('GET', '/api/v1/passives/tree', token=token)['data']
    assert len(tree['nodes']) == 115
    request('GET', root, token=other_token, expected=404)
    state = request('GET', root, token=token)['data']
    assert state['availablePoints'] >= 1 and not state['allocated']
    def command(action, node=None):
        return dict(expectedVersion=state['characterVersion'], treeRevision=state['treeRevision'], requestId=uuid.uuid4().hex, action=action, nodeId=node)
    request('POST', root, command('ALLOCATE', 'vitality_9'), token, 400)
    request('POST', root, command('ALLOCATE', 'origin'), other_token, 404)
    before = state['stats']['values']['maximum_life']
    allocate = command('ALLOCATE', 'origin')
    state = request('POST', root, allocate, token)['data']
    assert state['stats']['values']['maximum_life'] == before + 5
    assert state['allocated'] == ['origin'] and state['spentPoints'] == 1
    assert request('POST', root, allocate, token)['data'] == state
    request('POST', root, dict(allocate, requestId=uuid.uuid4().hex), token, 409)
    request('POST', root, dict(allocate, action='RESET', nodeId=None), token, 400)
    assert request('GET', root, token=token)['data'] == state
    request('PUT', '/api/v1/character?id=' + cid, {'expectedVersion': state['characterVersion'], 'changes': {'passiveNodes': ['vitality_9'], 'passiveTreeRevision': 999}}, token, 400)
    battle_root = '/api/v1/combat/characters/' + cid
    battle = request('POST', battle_root + '/start', dict(expectedVersion=state['characterVersion'], requestId=uuid.uuid4().hex, zoneId='coast'), token)['data']
    assert battle['battle']['hero']['maxLife'] == state['stats']['values']['maximum_life']
    state = request('GET', root, token=token)['data']
    assert state['lockedReason'] and not state['allocatable'] and not state['refundable']
    request('POST', root, command('RESET'), token, 400)
    request('POST', battle_root + '/act', dict(expectedVersion=battle['characterVersion'], requestId=uuid.uuid4().hex, battleId=battle['battle']['id'], action='FLEE'), token)
    state = request('GET', root, token=token)['data']
    reset = command('RESET')
    state = request('POST', root, reset, token)['data']
    assert not state['allocated'] and state['availablePoints'] == state['totalPoints']
    assert state['stats']['values']['maximum_life'] == before
    assert request('POST', root, reset, token)['data'] == state
    print('Passive HTTP checks passed: persistence, ownership, points, CAS, idempotency, shared stats, combat lock and reset')
