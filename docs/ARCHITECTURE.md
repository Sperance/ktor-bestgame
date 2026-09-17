# Architecture and migration — 0.8.0

## Package ownership

| Package under `ru.descend` | Responsibility |
| --- | --- |
| `bootstrap` | Single YAML-driven application entry point and lifecycle |
| `bootstrap.di` | Separate Koin modules and their composition |
| `bootstrap.seed` | Startup orchestration and optional development identities |
| `domain.enums`, `domain.modifiers`, `domain.stats` | Shared game values, modifier expressions and resolution |
| `features.<feature>.model` | Character, equipment, user, item, recipe, redemption-code and block-list models |
| `features.<feature>.persistence` | Feature-specific Mongo repositories and validation |
| `features.<feature>.http` | Existing HTTP routes |
| `features.poe.domain` | Pure item generation, currency rules and inventory transitions |
| `features.poe.catalog` | Catalog loading, integrity rules and equipment projection |
| `features.poe.application` | Character ownership, version checks, transactional craft/drop orchestration |
| `features.poe.persistence` | Mongo snapshots and idempotency receipts |
| `features.poe.seed`, `features.poe.migration` | Bounded imports and legacy reference conversion |
| `features.poe.http` | PoE routes and separate request/response classes |
| `infrastructure` | Mongo transactions, caches, backups, logging, monitoring, HTTP plugins and crypto |
| `shared` | Persistence field names, errors, response envelopes and common extensions |

This is a reorganized single Gradle module, not a claim of independently deployable services or strictly isolated Gradle modules.
[The path map](package-migration.json) records every pre-existing production Kotlin file's move.
New contracts and Koin modules have additionally been extracted into their own files.

## Preserved boundaries

- Keep dependency declarations/versions and existing plugin installation paths. Ktor's EngineMain now reaches the actual application module.
- Keep Mongo collections based on entity **simple names** and all existing `_id` derivations.
- Explicit `@SerialName` pins old serialized type names, especially `features.data.equipment.equipment_data.Weapon`, `Armor`, `Accessory`.
- Keep paths and envelopes used by ExileForge. Contract tests post real Equipment JSON through Ktor ContentNegotiation after package moves.
- Keep immutable modifier revisions, character version CAS, transaction receipts, owner checks, and currency debit in the same transaction as the equipment update.
- Keep bounded, restartable seeding and the earlier transient-transaction retry fix. Generic repository wrappers now also propagate cancellation and transient transaction labels.

## Runtime changes

The composition root starts Koin, configures HTTP plugins, seeds, and installs routes before serving requests.
Application shutdown stops backup and monitoring workers and closes Koin. Demo identities are opt-in and receive a supplied password.
Version 0.9 removes the legacy query-key shutdown route. CRUD and diagnostics require JWT authentication with current database account state. Ownership, field allowlists and versioned commands replace the legacy access policies; see [API security audit](API_SECURITY_AUDIT.md) and [character commands](CHARACTER_COMMANDS.md).

Shared cache collections publish immutable list snapshots atomically; callers get a list copy.
Cached entity objects retain their existing mutable model API: this is container safety, not deep immutability.
Mongo pagination now uses `skip = page * pageSize` and validates bounds; ID lookups use persisted string IDs.

## Existing databases

Startup never drops collections, truncates old catalogs or rewrites player inventories.
Only compact IDs, explicitly published custom definitions (`catalogProfile=custom`) and requested historic references are loaded into an operational snapshot.
Old export definitions outside the profile cannot enter new random affix generation. Exact old revisions remain resolvable by ID/revision.
Old custom definitions without profile metadata must be republished to opt in; references to their old revision remain readable.

For a small database on disk, use a separate empty database name. Switching the active profile intentionally does **not** erase old documents.
For an old item's base outside the selected set, crafting returns a clear profile error; add that reviewed base and its implicits to support it again.
The old rollback branch can read retained data and old revisions, but does not understand the two new unique definitions; do not craft new-profile uniques with an old binary.
