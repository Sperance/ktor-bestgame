# CLAUDE.md

Guidance for AI assistants working in this repository.

This is **ktor-bestgame**, the Ktor + MongoDB + Koin RPG server. Its client is a separate
repository, **ExileForge** (Android Compose), whose own `CLAUDE.md` carries the full contract
between the two. The client is deliberately thin: this server owns items, stats, modifier rolls,
prices and inventory, and the client only renders what it is told.

## Как себя вести (жёсткие правила, выше всего остального)

Ты — молчаливый автономный coding-агент в git-репозитории.

Цель: выполнить задачу в коде и закоммитить результат. Общение с пользователем — только служебный статус.

Жёсткие правила вывода:

- Не выводи: код, diff, patch, содержимое файлов, команды, планы, рассуждения, объяснения, резюме, списки, markdown.
- Не извиняйся, не пиши лишний текст.
- Не пересказывай задачу.
- Все действия выполняй инструментами: чтение/запись файлов, bash, git. Не печатай команды — выполняй их.
- Финальный ответ — одним предложением.
- Если задача ясна — не спрашивай подтверждения. Если неоднозначна — вкратце с примером спрашивай.
- Не проси показать diff: изменения уже в git.

## Where the backlog lives

The owner tracks both repositories in one Asana project, **KTOR**
(`https://app.asana.com/1/1209764106123448/project/1218745509544018`, gid `1218745509544018`),
split into three sections: `Сервер · ktor-bestgame`, `Приложение · ExileForge` and `Обсудить`.

**A comment on a card is the owner's answer to it.** That is how decisions arrive: the card states
the problem, the comment states what to do. Read the comments before starting anything the board
already covers, and when a card's work is done, say so on the card rather than only in the reply.

## Standing rules (never skip, whatever the task)

These two were set by the owner of the project and outrank convenience. They apply to every
change, here and in the client, whether or not the task mentions them.

1. **Anything with a name is born translated.** Adding an item, a piece of equipment, a class, a
   currency orb, a tree node, a modifier, an enum value or an error code means adding its strings
   to **every** language `locale/index.json` lists — today `src/main/resources/locale/ru.json` and
   `en.json`, and whatever it lists tomorrow. A code without a name in all of them is an
   unfinished change, not a change with a follow-up.

   **The name of a thing that is traded is English in every language** (since 0.22.0), as in
   PoE, where players trade and search by one name — and since 0.25.0 it is written **once**:
   `equipment.<CODE>.name`, `item.<CODE>.name` — orbs included — and `enum.EnumCurrencyOrb.*`
   live only in `locale/common.json` (`LocaleCache.commonKey`), never in `ru.json`/`en.json`.
   `LocaleCache` serves each language as `common.json` + that language merged, hashes the merged
   text, and refuses to start when a key is in both. Their descriptions, the modifiers, classes,
   tree nodes, stats and errors are translated as usual, so a new item gets its name in
   `common.json` and a description in every language file.

   The same rule runs on the client, where it covers the other half: every label ExileForge wrote
   itself is a key in its own `core/src/main/resources/i18n/ui_{ru,en}.json`, and its
   `UiStringsTest` is the twin of the test below. A language exists for a player only when both
   halves have it, which is why the client builds its picker from this server's manifest.

   Keys are `<section>.<CODE>.<field>`, built by `LocaleKey` on both sides; never hand-write one,
   because the client computes the same string and a drift shows up as a raw key on screen.

   `LocalizationTest` is what catches a miss, and it is stricter than it looks: every dictionary
   must cover every key the code asks for and carry no extras, the languages must hold identical
   key sets, no string may be empty, a placeholder must survive translation, and a composite
   modifier needs a placeholder per effect, and an item's name must be the English one in every
   language. Run it before calling such a change done.

2. **Every finished change ends with a changelog entry and a version.** Once the checks have
   passed and the branches are pushed, write what changed into `CHANGELOG.md` — a new entry at
   the **top**, dated, under the version number, brief: what moved, and for a fix, what the cause
   was. The client keeps its own `CHANGELOG.md` and the two cross-reference each other, so the
   history of dates answers "what changed and when" without reading commits. Then report the same
   list in the reply, **for the application and for the server separately** — even when only one
   of them moved, say so. The server's number is `SERVER_VERSION`, pinned in the client's
   `core/.../contract/Contract.kt` together with `SERVER_COMMIT` and `SERVER_BRANCH`; bump it as
   part of the change rather than leaving it for later. This is the last step of the work, not a
   courtesy: a change that is pushed but not written up is not delivered.

## Access and configuration

Since 0.21.0 every request passes `server/addons/Access.kt` before its route. `AccessPolicy.need`
is one pure table of who may call what — public (sign-in, `/locale`, `/icons`,
`/system/{routes,health,version}`), signed in, or ADMIN — and `AuthTest` reads it without a
database. Keep new rules in that table rather than in a route.

- **A session is a token.** `POST /api/v1/user/login`, `/login/byDeviceId` and `/byDeviceId` take
  a JSON body and answer `{user, token}`; the token goes back as `Authorization: Bearer`. Only its
  SHA-256 is stored (`authsession`), it lives 30 days and every use extends it. `GET /user/me`
  restores a session, `POST /user/logout` ends one, and a password change ends all the others.
- **Ownership is checked once, centrally.** A non-admin caller's `userId` and `characterId`
  query parameters (and `id` on `/api/v1/character`) must be their own; repositories already
  check that an item or a lot belongs to that character. `caller()` reads the `Caller` from the
  coroutine context when a repository needs to know who asked.
- **The generic CRUD writes only for an administrator**, except creating and deleting one's own
  character; the player-created character is reset to level 1 with nothing in it. The private
  collections (`AccessPolicy.privateCollections`) are read whole only by an administrator.
- **Passwords are PBKDF2** (`pbkdf2$<iterations>$<salt>$<hash>`); a legacy SHA-256 hash still
  verifies once and is rewritten on that login. Secrets never travel in a query string.
- **Nothing secret is in the source.** `MONGO_URI`, `MONGO_DB`, `PORT`, `CORS_HOSTS`
  (comma-separated origins; unset means no CORS), `ADMIN_PASSWORD` and `TEST_PLAYER_PASSWORD`
  come from the environment. Without `ADMIN_PASSWORD` no administrator is seeded, and without
  `TEST_PLAYER_PASSWORD` no test player — the client's `client-server` job sets both.
- Sign-in is limited to 10 requests a minute per address, everything else to 600 per client, and
  an unexpected exception answers `SP_500` without its message.

## Where names and numbers live

- `CHANGELOG.md` — dated entries per version; the client keeps its own beside it.

- `src/main/resources/locale/{index,common,ru,en}.json` — every string in the game; `common.json`
  holds what is the same in every language and is merged into each on the way out. No document in Mongo
  has carried text since 0.14.0; entities store a `code`.
- `src/main/resources/content/{equipment,items,currency,bench,pools}.json` — the catalogues, read by
  the seeders; `bench.json` is the crafting bench, one line per crafted modifier tier and its price
  in orbs, and `pools.json` the modifier pool of every slot as a list of codes (since 0.24.0). A
  base's *local* affixes are not listed there: `EquipmentSeeder.localPool` adds every natural local
  affix whose stats the base itself carries, so an armour base rolls armour and a hybrid rolls both.
  Since 0.24.0 the ordinary bases are Path of Exile's own, five per defence type or weapon kind.
- `features/logic/equipment/EquipSlots.kt` — where an equipped item goes and what it takes off:
  a two-handed weapon frees both hands, a bow pairs with a quiver and any other one-handed weapon
  with a shield, and a ring takes the free one of `RING`/`RING_2` (or the one `equip?slot=` names).
  `RING_2` is only ever an `equippedSlot`, never a template's slot. Uniques stay in Kotlin: each generates its own `ModifierDefinition`s with tier ranges,
  which is a rule rather than data.
- `src/main/resources/content/campaign.json` — the campaign (since 0.26.0): chapters of maps in
  unlocking order, each with its level, biome and two to four monsters; monsters at level 1 with
  a `form` the client draws and a loot table; monster modifiers, each with the lowest rarity that may roll it
  (`minRarity` — a rare draws from a wider pool); the three monster rarities with their weights,
  modifier counts, `statScale` (a MORE to every `growth` stat, expanded into `effects` when served),
  `modifierPower` (how much stronger their modifiers roll) and loot/experience multipliers; and `growth`, how
  each stat rises per map level. `CampaignContent` validates it at start and serves it resolved —
  monsters and `ADD` modifiers already scaled to their map — from
  `GET /api/v1/character/campaign/chapters`. **The fight is the client's** (the owner's decision):
  it rolls the monster's rarity and modifiers from these tables, fights, and reports
  `POST /campaign/kill?characterId&mapCode&monsterCode&rarity`; `CampaignService` checks the map is
  open and the monster lives there, then rolls gold, orbs, equipment and experience itself
  (`CampaignLoot`) in one transaction. `POST /campaign/complete` marks a map cleared
  (`Character.campaign`) and opens the next; `GET /campaign/progress` answers both lists.
  Monster, map, chapter and monster-modifier names are `monster.*`, `map.*`, `chapter.*` and
  `monstermod.*` keys, translated in every language; `CampaignTest` reads the file without Mongo.
- `src/main/resources/skilltree/tree.json` — the passive tree, 299 nodes. `SkillTreeSeeder` only
  reads and validates it.
- `config/ModifierSeeder.kt`, `config/ProgressionSeeder.kt` — modifiers, classes and the level
  table, in code because they are rules.
- `src/main/resources/icons/` — outline path data, no raster images.
- `src/main/resources/portraits/{class,form,monster}/<CODE>.svg` — portraits (since 0.29.0), three
  by four (`viewBox 0 0 300 400`), the face in the circle (150, 165) r 120 that the client cuts out
  as the map's token. `PortraitCache` looks for a file under every class, monster form and monster
  code and serves `portraits/index.json` (a fingerprint per file) and the files. The client draws
  the SVG itself, so only `path`, `circle`, `ellipse`, `rect`, `g` and user-space gradients are
  allowed — `PortraitTest` refuses anything else, and a class or form without a file.

How a modifier lands on an item (since 0.23.0): `ModifierRoller.pickAffixes` draws by
`spawnWeight` and never puts two modifiers of one `group` on an item. A template's pool holds
only *natural* affixes; `influence` modifiers join the pool of an item that carries that influence
(Shaper's Orb, Elder Orb), and `crafted` ones are placed only by `CraftingBench`. A `fractured`
affix (Fracturing Orb) is untouched by every orb. A new crafted modifier needs a line in
`bench.json` and shares its natural twin's group; `ModifierRollTest` checks both.

A code derives its `_id` through `toStableObjectId()`, so reseeding never breaks a reference that
a character already holds.

## Build and verification

```bash
bash gradlew build          # compiles and runs the test suite
bash gradlew installDist    # what the client's CI job boots
```

`gradlew` is committed without the executable bit, so invoke it as `bash gradlew`.

Tests that need a live MongoDB (`MongoTest`, `AuctionTest`, `CurrencyTest`, `StatsTest`,
`PagingTest`, `SoftDeleteTest`) **fail** without one rather than skipping — in a bare container
that is expected, and is not a signal that the change broke something. `LocalizationTest`,
`SkillTreeTest`, `SeedDataTest` and `ModifierRollTest` read resources only and must pass everywhere, which is why
they are the ones rule 1 leans on. Run a single one with
`bash gradlew test --tests LocalizationTest`.
