# PoE catalog and inventory crafting

This branch targets **PoE 1, export 3.29.3.3**, without item sockets or skill gems in equipment.
It is an implementation of the catalog, ordinary wearable item generation and the listed currency rules,
**not a complete implementation of every Path of Exile combat mechanic**.

## Data and reproducibility

- Source: https://github.com/repoe-fork/repoe-fork.github.io/tree/e2bd511a0133bbe6c1ab548ef1285cb99f3cf0e9/data
- 5,461 complete base records and 40,355 complete modifier records. These include monster, area,
  legacy, crafted and other domains, not just ordinary item affixes.
- `data/poe.lock.json` pins the commit and SHA-256 of both source files. Build requires Python 3
  and access to raw.githubusercontent.com. `preparePoeCatalog` verifies and packages gzip resources.
  Startup requires no internet. For offline builds, download the two pinned files first, then run
  `python3 scripts/prepare_poe.py --source-dir /path/to/data` before Gradle.
- Game data belongs to Grinding Gear Games. RePoE attribution/license is in `data/REPOE-LICENSE.md`.
- Unique names/art alone are insufficient to reconstruct unique modifiers and drop restrictions.
  This export is not a verified global unique drop table. No invented unique items are seeded.

## Seeder and storage

`PoeSeeder` inserts all released wearable bases as Equipment, all other released base records as
Items, and every exported modifier as ModifierDefinition. The complete unfiltered source tables
are also available through the catalog API. A catalog entry does not automatically enter ordinary loot.

IDs are stable SHA-256-derived 24-character IDs. Seeding adds missing records in batches of 100,
never deletes the equipment collection, never overwrites an administrator's edited template and
never grants another starter item on restart. Existing unrelated/legacy data remains intact.
Run only one seeder during initial deployment; simultaneous initial seeds can conflict on IDs.
Updating the data lock does not silently rewrite persisted items: an explicit migration is required.

Each CharacterEquipments has an optional `poe` snapshot: base ID, item level, PoE rarity, implicits,
explicits with independently rolled integer values, fractured flags, quality, corruption, mirrored
state and catalog version. `params` mirrors these rolls for existing clients. Its `uuid` identifies
an individual item; `equipmentId` identifies the shared template. Legacy items are retained and
cannot be crafted until explicitly migrated. Old rarity enums are not reinterpreted as PoE rarity.

Original base properties (armour, evasion, ES, ward, requirements, etc.) remain in the base catalog.
The old `defense` scalar is only a compatibility projection and is not a complete PoE defence model.

## Generation and supported currency

Generation respects item level, first matching spawn weight (including zero), generation weights,
essence-only restrictions, modifier groups, added tags and magic 1-prefix/1-suffix vs rare 3/3 limits.
Implicit IDs come from the actual base, with inclusive integer range rolls. Mod IDs identify exact
exported tiers; generic `tier=1` is a compatibility field, not a claim that every imported roll is T1.

| Currency ID | Implemented behavior |
| --- | --- |
| TRANSMUTATION | Normal → magic, 1–2 explicit affixes |
| AUGMENTATION | Add one affix to magic, respecting 1/1 cap |
| ALTERATION | Reroll magic explicits, retain protected modifiers |
| ALCHEMY | Normal → rare, 4–6 affixes |
| CHAOS | Reroll rare explicits, retain protected modifiers |
| REGAL | Magic → rare and add one, retain previous affixes |
| EXALTED | Add one to rare, at most 3 prefixes and 3 suffixes |
| SCOURING | Remove unprotected explicits; rarity follows survivors |
| ANNULMENT | Remove one unprotected explicit; keep rarity |
| DIVINE | Reroll values within the same exact modifier IDs/ranges |
| BLESSED | Reroll base implicit values, leave explicits unchanged |
| FRACTURING | Lock one random explicit on an unfractured rare with ≥4 affixes |
| MIRROR | Add a separate mirrored copy with a new UUID; retain original |

Corrupted, mirrored, influenced and unique equipment are rejected by this ruleset. Prefix/suffix
locks and cannot-roll-attack/caster restrictions are recognized for the implemented operations;
there is no bench API to create those modifiers yet. Fractured values and IDs survive reroll/removal.
No eligible affix, wrong rarity, full slots or invalid state cause no currency debit.

Vaal, Chance, influence/Eldritch, fossils, essences, bench and veiled operations are **not implemented**.
They are absent from `/currencies`; unsupported requests cannot accidentally consume another currency.
Socket/gem mechanics are outside this game's scope. Flasks, jewels, maps and league-specific items are
catalogued, but this crafting route currently accepts wearable weapons, armour and accessories only.

Administrative random drops use a conservative base filter (no talismans or known special base tags).
This is not a complete league/area/boss drop table. Base selection is uniform; normal/magic/rare weights
are 50/35/15. Rare affix-count weights are 8/3/1 for 4/5/6. Those probabilities are server balance choices,
not claimed to be the undisclosed live GGG drop probabilities.

## Modifier runtime

All original records, raw stat IDs, ranges, text, groups, tags and granted effects are retained.
`PoeEffectRegistry` provides explicit, extensible adapters for common attributes, life, mana and resistance
stats. `runtimeSupported` and `unsupportedStats` report missing adapters. Importing a modifier is not
proof that its combat behavior is implemented; conversion, ailments, triggered skills, conditional
unique effects and other subsystems still need handlers. Unknown effects are never guessed from names.

The legacy stat engine now carries each modifier's rolled values into expressions. Increased/reduced
share one additive bucket; more/less multiply; min/max clamp the final result. Percentage operations in
that engine take fractions (0.20 = 20%); resistance FLAT adapters use percentage points.

## API

All responses use the existing `success/data/error` envelope.

- `GET /api/v1/poe/catalog?type=bases|modifiers&page=0&size=50&q=Strength`
  returns `{items:[{id,data}],page,size,total}`. `id` retrieves an exact original ID. Size ≤200.
- `GET /api/v1/poe/modifier-definition?id=Strength1` returns the compatibility definition,
  original `poe` record and runtime coverage flags.
- `GET /api/v1/poe/capabilities` reports explicit coverage and probability policy.
- `GET /api/v1/poe/currencies` returns supported IDs, names and inventory `itemId` values.
- `POST /api/v1/poe/token` with `{"login":"...","password":"..."}` returns a one-hour JWT.
- `POST /api/v1/poe/characters/{characterId}/craft`, with `Authorization: Bearer TOKEN`:

```json
{"requestId":"craft_00000001","equipmentUuid":"INSTANCE_UUID","currency":"ALCHEMY","expectedVersion":0}
```

- `POST /api/v1/poe/characters/{characterId}/drop`, JWT of an ADMIN **owning that character**:

```json
{"requestId":"drop_000000001","expectedVersion":0}
```

Drop is an administrative testing endpoint. Gameplay should grant loot from a verified server-side
reward event. It is deliberately not an unlimited loot faucet for regular player tokens.
Read the character through the existing GET route to obtain `version`, equipment UUIDs and currency.
Responses include the resulting equipment, character version and (for crafting) remaining currency.

The item update, currency debit, version increment and permanent request receipt commit in one MongoDB
transaction. Repeating an identical request returns its stored result. Reusing a request ID with a
different payload fails. Concurrent writes use the existing version CAS; retry an uncertain response
with the **same** request ID and payload, never a freshly generated ID. Request IDs are 8–80 ASCII
letters/digits/underscore/hyphen. Replica set MongoDB is required; standalone MongoDB cannot transact.

Set `MONGO_URI` and `MONGO_DB` for your deployment. Set a stable `JWT_SECRET` of at least 32 characters;
without it, a random process-local key is used, so restart invalidates tokens. Use HTTPS for credentials.
The repository's legacy CRUD/admin endpoints still have their original authorization model; these new
JWT checks do not turn the entire existing server into a hardened public multiplayer service.

## Verification

`./gradlew poeTest jar` runs deterministic catalog/crafting/stat regression tests and packages resources.
`MONGO_DB=poe_integration_test MONGO_URI='mongodb://localhost:27017/?replicaSet=rs0' ./gradlew poeMongoTest`
runs persistence, replay, concurrency, failure and repeated-seeder tests against a dedicated replica set.
The workflow starts an isolated MongoDB 8 container. Legacy ApplicationTest/MongoTest are separate,
stateful suites; this workflow does not claim those pre-existing tests pass.
