# ktor-bestgame — compact RPG server

Kotlin/Ktor server with MongoDB, a small tiered PoE item catalog and character-owned crafting.
Version **0.9.0**. This restructuring lives only on `refactor/compact-rpg-architecture`.

## Security and equipment update

Read [API audit and client migration](docs/API_SECURITY_AUDIT.md) and [character commands / calculation rules](docs/CHARACTER_COMMANDS.md) before updating ExileForge. Writes now require authentication, allowlisted DTO fields and expectedVersion. Old password-in-GET/device-login prototypes are removed.

## Run

Requirements: JDK 21, Python 3, MongoDB replica set (transactions are required).

```bash
export MONGO_URI='mongodb://localhost:27017/?replicaSet=rs0'
export MONGO_DB='bestgame_compact'
export JWT_SECRET='<your-random-secret-at-least-32-characters>'
./gradlew run
```

On Windows use `gradlew.bat run` and set the same environment variables in PowerShell.
Ktor reads `src/main/resources/application.yaml`; the default port is 8080.
`./gradlew installDist` produces a runnable distribution under `build/install/ktor-bestgame`.

Seeding is additive and restartable. It does not delete existing data or grant duplicate items.
For an optional development administrator and one character, set `SEED_DEMO_DATA=true` and
`SEED_ADMIN_PASSWORD` (at least 12 characters) **before the first startup of an empty database**.
Existing accounts/passwords are never overwritten. Default credentials are not embedded in the server.

## Compact catalog

| Content | Count |
| --- | ---: |
| Ordinary equipment bases | 35 |
| Curated unique equipment | 2 |
| Supported currency item templates | 13 |
| Basic affix families | 24 |
| Tier records in those families | 208 |
| Total modifier definitions, including implicits and unique properties | 234 |

The ordinary set covers early, middle and late equipment progression. Jewellery is deliberately limited.
The unique set is **Blackheart** and **Le Heup of All**, with fixed explicit property sets and rolled values.
Unique instances accept Divine and Blessed Orbs; ordinary rarity-changing currencies and Mirror are rejected.
There are no sockets or skill gems. Imported effect ranges are preserved; unsupported combat effects are
reported through `runtimeSupported` / `unsupportedStats`, not silently treated as implemented mechanics.

Data is checked in under `data/poe/compact`, with SHA-256 checksums in `data/poe.lock.json`.
Building and starting the server do not download the full PoE export. See [catalog maintenance](docs/COMPACT_CATALOG.md).

## Architecture and compatibility

All Kotlin production packages are under `ru.descend`. See [architecture and migration](docs/ARCHITECTURE.md).
The versions and declarations in `gradle/libs.versions.toml` are preserved: Ktor plugins, Netty, Koin,
MongoDB/BSON, ktmongo, kotlinx.serialization, kotlinx.datetime, Logback, Swagger annotations and Dokka.
Existing integrations remain in the project; this change does not replace the database or dependency stack.

Mongo collection names, IDs, existing modifier revisions and polymorphic JSON discriminator strings are retained.
Catalog and PoE paths remain available; protected CRUD and inventory bodies follow the new versioned command contract. The default catalog listing
is smaller. Old equipment outside the compact base set remains stored and readable, but cannot be crafted
until its base is deliberately added to the active catalog. No automatic destructive cleanup is performed.
Existing custom modifiers can be reactivated through publication; new custom publications participate in the active catalog.

## Verify

```bash
./gradlew test
MONGO_URI='mongodb://localhost:27017/?replicaSet=rs0' MONGO_DB=poe_integration_test ./gradlew poeMongoTest
./gradlew installDist
MONGO_URI='mongodb://localhost:27017/?replicaSet=rs0' MONGO_DB=poe_startup_test python3 scripts/smoke_server.py
```

CI uses disposable MongoDB databases. It checks unit/HTTP serialization contracts, transactional crafting,
seed restart/concurrency, then starts the packaged server twice and checks its real HTTP API.
The old manual `MongoTest` is excluded from the default test task; it is retained as an opt-in legacy test.
API details: [PoE routes](docs/POE.md), [modifier revision storage](docs/MODIFIER_STORAGE.md).
