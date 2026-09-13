# Compact catalog maintenance

`data/poe/compact/base_items.json` contains 35 ordinary bases, two unique definitions and 13 currencies.
`mods.json` contains 234 original modifier records. `tiers.json` gives 208 records a family and a T1-first rank within the 24 selected families.
Each original tier ID remains a separate immutable Mongo modifier definition. IDs, ranges, spawn weights and required levels are retained.
Ranks describe this selected family set, not a universal ranking across influenced, essence, legacy and other excluded variants.

Families: life, mana, strength, dexterity, intelligence, fire/cold/lightning/chaos resistance,
flat armour/evasion/energy shield, armour percentage, movement speed, local physical damage,
local added physical damage, physical damage/accuracy hybrid, attack speed, spell damage,
added fire/cold/lightning attack damage, mana regeneration and critical chance.

## Data provenance

- Original base and modifier records: [RePoE pinned export](https://github.com/repoe-fork/repoe-fork.github.io/tree/e2bd511a0133bbe6c1ab548ef1285cb99f3cf0e9/data), version 3.29.3.3.
- Two unique compositions: [pinned Path of Building ring definitions](https://github.com/PathOfBuildingCommunity/PathOfBuilding/blob/16de4b82d57f1c0de6eb40f37143c32d4da36a02/src/Data/Uniques/ring.lua), current variants.
- Game data copyright Grinding Gear Games; no art assets are copied.

Unique records have stable project IDs `ExileForge/Uniques/blackheart` and `ExileForge/Uniques/le-heup-of-all`,
plus `unique_base_id` and `unique_mods`. They use the original Iron Ring base properties and implicits.
The extra base IDs let the existing equipment-ID relationship identify a unique template without breaking the Android contract.
The original unique modifiers are loaded into Mongo like other definitions; each instance contains pinned references and independently rolled values.

## Selection and probabilities

Base selection is uniform among eligible compact equipment records at the character's level, including curated uniques.
For ordinary bases, rarity weights are 50% normal, 35% magic, 15% rare. Rare target counts 4/5/6 have weights 8/3/1.
If no eligible modifier group remains, generation stops with the available affixes. Explicit augmentation/exalt operations still reject a lack of eligible affixes without charging currency.
These are project balance rules, not a reproduction of undisclosed GGG drop probabilities.
Unique affix sets never enter ordinary rolling. Divine rerolls their explicit values; Blessed rerolls eligible implicit ranges.
Other unique currency operations are rejected before inventory mutation.

## Editing

1. Add/remove reviewed records in the three JSON files. Keep ordinary implicit and unique explicit references complete.
2. Update counts/selection metadata and SHA-256 hashes in `data/poe.lock.json`. Do not modify old raw records casually: existing Mongo revisions are immutable.
3. Run `python3 scripts/prepare_poe.py`, then `./gradlew test` and the Mongo integration task.
4. Update the catalog-count assertions and documentation if intentionally changing the fixed starter set.
5. For new versions of already imported definitions, publish a new revision with the existing admin API; additive seeding does not overwrite revision 1.

The build packages checked-in files only and fails on a checksum mismatch. There is no startup/build network import of all PoE data.
Unsupported combat effects remain explicitly reported. This change covers data, rolling, inventory persistence and currency transitions; it does not claim a complete PoE combat engine.
