# Workbench API — 0.10.0 / revision 3

ExileForge 1.7 uses these additions. MongoDB, dependencies and existing write commands remain in place.

## Equipment comparison

Authenticated POST `/api/v1/character/{id}/compareEquipment`:

```json
{"expectedVersion":4,"equipmentUuid":"0123456789abcdef01234567","slot":"RING_LEFT"}
```

Returns `EquipmentComparison(characterVersion, allowed, reason, before, after)` inside the normal envelope. `before` and `after` use the existing CharacterStats model. An invalid slot, attribute requirement or two-handed loadout returns `allowed=false` and a reason; `after` is null. Ownership and existence failures use 404, stale versions use 409. No inventory, version, currency, receipt or template is written. Confirmation calls the existing `/equip` with the same expectedVersion; intervening changes still conflict.

## Craft options

Authenticated GET `/api/v1/character/{id}/craftOptions?equipmentUuid=...` returns the character version and a list of `{currency,name,itemId,amount,available,reason}`.

Availability checks ownership, currency balance and the existing pure crafting engine against the current stored instance. A deterministic throwaway RNG is used, and its generated item is discarded. This is an eligibility hint, not a prediction of random results. The actual command still checks current version and the requirements of the resulting equipped loadout atomically. No currency is consumed by this endpoint.

## Catalog search

Authenticated `/api/v1/{collection}/paged` and `/count` accept:

| Parameter | Meaning |
| --- | --- |
| `q` | Literal case-insensitive search, up to 100 characters; applied before paging |
| `slot`, `rarity` | Equipment equality filters |
| `minLevel`, `maxLevel` | Equipment item level, 1–100 |
| `stat`, `minStat` | Numeric threshold for damage_min, damage_max, defense or attackSpeed |
| `modifierId` | Exact modifier definition ID in ordinary or stock references |

The same filter is applied to result and total count. Owner/soft-delete restrictions remain mandatory. User-supplied field paths and Mongo operators are not accepted. These filters target template properties/references, not arbitrary calculated PoE combat stats.

Capabilities advertise `apiRevision=3`, `equipmentComparison`, `catalogSearch`, `craftOptions`. Server CI covers preview non-mutation, rejected slots, foreign ownership, filtered totals and invalid field selectors. ExileForge CI additionally runs the actual Kotlin client against a packaged server and isolated MongoDB replica set.
