# Text combat, rules revision 1

Routes require a current JWT. Gameplay only accepts the character's actual owner, including for administrators.

- GET /api/v1/combat/catalog — zones, monsters, bosses and weighted loot tables. Zones and monsters carry `icon`
  (bosses their own, ordinary monsters by damage element); it is added on response, never stored. See [ICONS.md](ICONS.md).
- GET /api/v1/combat/characters/{id} — current/last encounter and boss progress.
- POST /api/v1/combat/characters/{id}/start — {expectedVersion, requestId, zoneId, boss:false}.
- POST /api/v1/combat/characters/{id}/act — {expectedVersion, requestId, battleId, action}.
- Actions: ATTACK, POWER (8 mana, 1.8 damage), GUARD (+6 mana, 65% reduction), POTION (40% life, two per encounter), FLEE.
- Three normal victories unlock a zone boss. Boss victory resets that zone's counter. Zone level is the minimum character level.
- Boss every third turn uses a telegraphed 1.8x attack. Maximum encounter length is 80 turns.
- Every new encounter starts at full life/mana/shield. Defeat/escape gives no reward and has no item loss.

## Calculation
EquipmentService's shared calculator supplies a snapshot at encounter start. Subsequent equipment/craft changes apply to the next encounter. Life, mana, shield, armour, evasion, accuracy, critical chance, regeneration and elemental/chaos resistances affect turns.
This is a compact text ruleset, not an exact PoE combat simulator: the strongest weapon's calculated DPS (including its added damage) is a single physical packet per second. Dual wield uses the strongest weapon. Unarmed damage is 6 + strength * 0.2. Enemy elemental damage respects resistance; chaos bypasses shield. Unsupported source stats remain visible in the response.
Hit chance = clamp(accuracy / (accuracy + evasion*0.5 + 1), 0.1, 0.98).
Physical reduction = min(armour / (armour + 5*damage), 0.9). Critical multiplier 1.5; damage variation 85–115%.

## Persistence and extensions
Character.battle and Character.zoneKills are server-managed and excluded from generic CRUD writes.
CombatWorldDocument/default stores the entire small world catalog in MongoDB. First read seeds it atomically with setOnInsert. Later restarts preserve edits. Edit zones, monsters and loot tables in that document; validation rejects invalid references, weights and stat ranges. An active battle pins its monster and loot table.
BattleReceipt stores each command payload and result. All actions, character CAS, reward generation and receipt insertion share one retryable Mongo transaction. Duplicate request IDs return the original response even after another battle; reuse with a changed payload is rejected. Receipts are retained; no TTL that could reopen reward duplication.
Reads and mutations enforce owner before receipt access. Competing commands cannot both advance the same version.
Only victory invokes loot generation: ordinary monsters get one weighted roll, bosses two. Gold and XP are guaranteed. Equipment uses the current compact PoE catalog, eligible item level and existing generation/modifier pipeline. If UNIQUE has no eligible base, it falls back to RARE. Currency stacks and equipment UUIDs enter the actual inventory.
Reserve two inventory slots before entering. If another command fills the inventory during combat, the finishing action rolls back; free space and retry. Total XP promotes level when XP >= 50*level*(level+1), up to 100.
No sockets or skill gems.

## Verification
BattleEngineTest checks turn rules, mitigation, resources, terminal states and catalog validation.
scripts/combat_smoke.py runs against the packaged server + real Mongo replica set: ownership, concurrent starts, idempotent starts/turns/rewards, boss progression, loot persistence and generic CRUD rejection.
