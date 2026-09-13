# API audit and migration — 0.9.0

Branch: `refactor/compact-rpg-architecture`. Database and dependency stack retained.
This release deliberately changes unsafe write contracts. Existing ExileForge builds need the client changes below before using the new commands.

## Access matrix

| Resource / operation | Unauthenticated | Player | Administrator |
| --- | --- | --- | --- |
| POST `/api/v1/poe/token` | Credentials only | Credentials only | Credentials only |
| PoE catalog, currencies, capabilities, modifier lookup; public key; health; Swagger | Public read | Read | Read |
| User list / count / paged / GET by ID | 401 | Own profile only | All active profiles |
| Character list / count / paged / GET by ID | 401 | Own active characters only | All active characters |
| Equipment/items/recipes read, cache hashes | 401 | Read | Read |
| Redemption-code catalog | 401 | 403 | Read/write |
| Catalog create/update/delete, user creation | 401 | 403 | Allowed, explicit field policy |
| Character creation | 401 | Current user is assigned on server | Current user is assigned on server |
| Profile / character edit | 401 | Own editable fields | Editable fields |
| Character deletion | 401 | Own, with expectedVersion | Allowed with expectedVersion |
| User deletion | 401 | 403 | Allowed except own account |
| Grant equipment / adjust item stacks | 401 | 403 | Explicit versioned commands |
| Equip/unequip/stats/redeem/instant recipe | 401 | Own character | Permitted administration |
| PoE craft/inventory | 401 | Own character | Own character |
| Administrative PoE drop / modifier publication | 401 | 403 | Allowed (drop targets own character) |
| Backup operations, system route/error listing | 401 | 403 | Allowed |
| Secure exchange | 401 | Authenticated | Authenticated |

A JWT is verified against MongoDB account activity, soft deletion and `authVersion`. Roles are read from MongoDB, never trusted from the request body or token claims.
Changing a password or role increments authVersion and invalidates previously issued tokens. Existing old tokens without this claim must be replaced by logging in again.

## Findings and resolution

- Unscoped CRUD: authentication and owner-filtered Mongo queries are now shared by list/count/paging/lookup/mutation paths.
- Mass assignment: normal profile fields are limited to name/email/age; character fields to name/description. Role, owner, money, stats, inventories, deletion flags, hashes, versions and generated IDs are not freely writable.
- Catalog writes: administrator-only; fields are allowlisted, decoded with the entity's real serializer, then validated. Nested unknown fields and malformed types are rejected.
- Optimistic locking: every CRUD entity now stores a version, including equipment, items, recipes and redemption codes. Legacy missing versions are treated as zero in CAS filters. Every update/delete requires the client's expectedVersion.
- Passwords: PBKDF2-HMAC-SHA256, 600,000 rounds, individual random salts and versioned encodings. Legacy salted SHA-256 is accepted at login and immediately upgraded. The new policy is 12–128 characters; valid old shorter passwords can migrate.
- Secret exposure: removed entity payloads from transaction names, secret-bearing decoder/driver errors from HTTP responses and decrypted messages from crypto logs.
- Removed authentication bypass prototypes: GET password login/change, device-ID login/creation, demo cookie login/profile/logout and query-key server shutdown are no longer registered. Cookie encryption/signing keys are derived separately from the configured server secret; the Sessions integration remains installed.
- Previous recipe/redemption implementations returned success without consuming/granting items. They are deprecated and fail closed. The new commands perform inventory transitions and counters in one transaction.
- JSON commands reject unknown fields and have a 256 KiB streaming body limit. Lists are bounded; legacy GET-all returns at most 100 results. Use `/paged` for the rest.

## HTTP errors

- 400: invalid/unknown fields, wrong slot, unmet requirements, unsupported recipe mode.
- 401: missing/invalid/revoked token.
- 403: authenticated caller lacks the required privilege.
- 404: resource absent, deleted or outside the caller's ownership scope.
- 409: stale expectedVersion or concurrent Mongo write conflict.
- 500: generic failure without database internals or submitted secret values.

No live user database is deleted or reset by this migration. Existing items begin unequipped (`equipped={}`). Old instances without baseSnapshot use their existing template as a read fallback; new grants capture a snapshot.

## Client migration

1. Obtain a fresh token with POST `/api/v1/poe/token`; attach it to all protected requests.
2. Use `version` returned with each editable resource.
3. Send PUT as `{ "expectedVersion": 3, "changes": { "name": "New name" } }`.
4. Send DELETE with JSON `{ "expectedVersion": 3 }`; it soft-deletes the entity.
5. A successful write returns the new version. On 409 reload and let the user review changes; do not automatically resend with a newly fetched version.
6. User/character POST still accepts an array, now containing explicit CreateUserCommand/CreateCharacterCommand fields. The server supplies owner and system fields. User creation is administrative.
7. Replace old inventory request bodies with the commands documented in CHARACTER_COMMANDS.md.
8. `GET /character/inventory/equipments` now returns EquipmentView with instances, slots, item stacks and computed stats. `/inventory/equipped` returns only equipped instances.

The branch includes unit/Mongo tests plus black-box checks against the actual packaged server. Test credentials are generated at runtime, not committed.
