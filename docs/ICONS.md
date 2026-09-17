# Icon set — forge-vector v1 / API revision 4

Every modifier, weapon, armour piece, currency orb, passive node, monster and character stat now has its
own drawn icon. The set is authored in this repository as vector art, rendered to SVG by the server and
served as a normal part of the API, so ExileForge and any other client can display it without bundling
pictures of its own.

- 120 original icons, grid 64x64, no external assets and no Path of Exile artwork.
- Categories: `WEAPON`, `ARMOUR`, `JEWELLERY`, `CURRENCY`, `STAT`, `AFFIX`, `RARITY`, `PASSIVE`, `COMBAT`, `ITEM`, `UI`.
- Deterministic: the same set always renders byte-identical SVG, so ETags and `version` are stable.
- Public: icon routes need no JWT. They carry no player data, and a client shows them before login.

## Routes

| Route | Returns |
| --- | --- |
| `GET /api/v1/icons` | Manifest: set id, revision, version, and every icon with title, category, colours and url. `?category=WEAPON` and `?q=sword` narrow it. |
| `GET /api/v1/icons/bindings` | Lookup tables: stat ids, catalog tags, item classes, weapon/slot/rarity/currency enums, passive kinds, battle actions and statuses, plus the icon of every bundled modifier and base. |
| `GET /api/v1/icons/{id}.svg` | One icon. `?variant=plain` drops the frame and glow and leaves only the drawing. |
| `GET /api/v1/icons/sprite.svg` | The whole set as `<symbol id="icon-...">`, for clients that prefer one request and `<use href="#icon-weapon-sword">`. |

JSON routes use the usual `{success,data,error}` envelope. SVG routes answer with `image/svg+xml`,
`ETag` and `Cache-Control: public, max-age=604800, immutable`, and return `304` for a matching
`If-None-Match`. An unknown icon id is `404`; an unknown `variant` is `400`.

`GET /api/v1/poe/capabilities` reports `icons`, `iconSet`, `iconSetRevision`, `iconSetVersion`,
`iconCount` and `iconsEndpoint`. Cache icons against `iconSetVersion`: while it is unchanged, nothing
in the set has changed.

## Icons inside existing responses

Nothing has to be looked up twice — the payloads that already carry an entity now carry its icon id:

- `GET /api/v1/poe/catalog` — every `PoeRecord` has `icon`, for both `type=bases` and `type=modifiers`.
- `GET /api/v1/poe/modifier-definitions` — `icons` maps `definitionId` to an icon id, including custom published definitions.
- `GET /api/v1/poe/currencies` — every `CurrencyOption` has `icon`.
- Equipment templates and instance snapshots (`Weapon`, `Armor`, `Accessory`) have `icon`; catalogued `Items` have `icon`.
- `GET /api/v1/passives/tree` — every node has `icon`.
- `GET /api/v1/combat/catalog` and the battle views — every zone and monster has `icon`.

The field is an icon **id**, never a URL to a foreign host: the picture is `/api/v1/icons/{icon}.svg` on the
same server. Documents written before this change have no stored icon, so equipment and item reads fill it
in on the way out (from the catalog base, else from weapon type or slot) without rewriting the database.
An embedded instance snapshot may still arrive with `icon: null`; resolve it through the binding tables
(`bases` by `poeBaseId`, `weapons`, `slots`) or fall back to `ui-unknown`.

## How an icon is chosen

`IconResolver` is deterministic and never guesses randomly:

1. the modifier's own stat ids (`base_maximum_life` → `stat-life`, `base_fire_damage_resistance_%` → `stat-fire-resistance`);
2. unknown stat ids are matched by parts (`...fire...resist...` → `stat-fire-resistance`), which also covers custom published modifiers;
3. catalog tags, most specific first (`fire` before `damage`);
4. the modifier source (prefix, suffix, implicit, unique, corruption, enchantment);
5. `ui-unknown` if nothing matched.

Base items use their item class, currency uses its display name, equipment uses weapon type or slot,
small passive nodes use the stat they grant, monsters use their element, bosses use `combat-boss`.
Every stat id and every base in the bundled compact catalog resolves to a meaningful icon — this is
enforced by tests, not by hand.

Icons are attached when a response is built and are not written to MongoDB. Redrawing the set never
rewrites stored battles, trees or catalogs. Administrators may set `icon` on equipment and items through
the usual versioned CRUD; the value must be an id that exists in the set.

## Client notes (ExileForge)

1. Read `/api/v1/poe/capabilities`. If `icons` is true, fetch `/api/v1/icons` and `/api/v1/icons/bindings` once.
2. Store both against `iconSetVersion` and `ETag`, and use conditional requests afterwards.
3. Render `icon` from a response; if it is missing, resolve the entity through the binding tables
   (stat id → `stats`, tag → `tags`, weapon → `weapons`, slot → `slots`, currency → `currencies`, rarity → `rarities`).
4. `variant=plain` gives artwork without the plate, for use inside the client's own frames. `tint` and
   `deep` from the manifest let the client colour its own vector shapes in the same palette.
5. Old clients are unaffected: every field is additive and API revision 3 behaviour is unchanged.

## Editing the set

Artwork lives in `src/main/kotlin/ru/descend/domain/icons/IconLibrary.kt`: one entry per icon, with id,
title, category, palette and an SVG fragment drawn in the 64x64 grid. The fragment inherits the stroke
from its group; `url(#core)` fills a shape.

```bash
./gradlew iconPreview   # build/icons: every svg, the sprite and preview.html with the whole set
./gradlew test          # the set, the routes and the resolution rules
```

Gradients use `userSpaceOnUse`. This is deliberate: with bounding-box units a straight line has a
zero-area box, the gradient becomes invalid and the stroke is not drawn at all — grips, crossguards and
separators would silently disappear.

Adding an icon: add the entry, point the relevant binding at it in `IconResolver`/`IconBindings`, run the
tests. Changing any drawing changes `iconSetVersion`, which is the signal for clients to refresh their cache.
Bump `IconRenderer.SET_REVISION` when the change is large enough that a client should re-fetch everything.
