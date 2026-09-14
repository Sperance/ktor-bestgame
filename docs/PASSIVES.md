# Shared passive skill tree (server 0.12.0)

Original PoE-inspired graph: 115 nodes, six branches, 18 notables and six keystones. Every node, including the origin, grants at least one stat. Keystone bonuses have tradeoffs; these are custom nodes, not an imported GGG tree.

MongoDB `PassiveTreeDocument/shared:1` contains the common versioned definition. The seeder uses set-on-insert and never overwrites a saved revision. Treat published revisions as immutable. Publish a new document under a new revision and explicitly migrate characters in a future balancing update; do not mutate an existing definition in place.
`Character.passiveTreeRevision` defaults to 1 and `passiveNodes` defaults to an empty set, so existing heroes need no destructive migration. Each hero has independent allocations. Points = clamp(level - 1, 0, 99); all current nodes cost one point. Existing levels immediately grant their earned points. No separately incremented counter can drift or be farmed by replaying rewards.

Allocation requires an affordable adjacent node on a connected path from the paid origin. Refund checks reachability of the entire remaining graph, including alternate routes. Reset/refund returns points for free in this initial balance version. Both are rejected if the new attributes make equipped items invalid. Remove such equipment first. All mutations are blocked during active combat.

## HTTP
All routes require JWT. Character routes require the actual owner, including administrators.
- GET `/api/v1/passives/tree?revision=1` — common graph, coordinates, edges and effects.
- GET `/api/v1/passives/characters/{id}` — allocations, earned/spent/free points, allocatable/refundable IDs, character version and calculated stats.
- POST `/api/v1/passives/characters/{id}` — `{expectedVersion,treeRevision,requestId,action,nodeId}`; action ALLOCATE, REFUND or RESET. RESET uses null/omitted nodeId.
- Capabilities include `passiveTree:true`. Generic character CRUD cannot write allocations or revision.

`PassiveReceipt` records the exact request/result in the same retryable Mongo transaction as the character CAS update. Duplicate IDs replay the committed result; altered payloads with the same ID fail. A stale version returns 409 without spending points. Keep receipt records for idempotency.

Effects use the existing DefaultStatResolver with source PASSIVE. Attribute bonuses participate in life/mana derivation and equipment requirements, resistance bonuses are capped after resolution. Attack multipliers affect weapon DPS and unarmed combat. The battle engine consumes the same snapshot as the hero equipment screen. The calculator reports unimplemented equipment effects separately.

Tests cover graph rules/cycles, budgets, keystone tradeoffs, isolation, derived stats, refund equipment requirements and real HTTP ownership, duplicate commands, snapshots, battle locking and reset.
