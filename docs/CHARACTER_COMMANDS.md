# Character commands and stats

All commands require a current bearer token. `{id}` is the character ID; equipment selection uses the **instance UUID**, not a shared template ID.

| Endpoint | JSON body |
| --- | --- |
| POST `/api/v1/character/{id}/equip` | `{"expectedVersion":0,"equipmentUuid":"…","slot":"RING_LEFT"}` |
| POST `/api/v1/character/{id}/unequip` | `{"expectedVersion":1,"slot":"RING_LEFT"}` |
| POST `/api/v1/character/inventory/itemToInventory?characterId=…` (admin) | `{"expectedVersion":2,"equipmentId":"…"}` |
| POST `/api/v1/character/inventory/addItem?characterId=…` (admin) | `{"expectedVersion":3,"items":[{"itemId":"…","amount":2}]}` |
| POST `/api/v1/character/{id}/redeem` | `{"expectedVersion":4,"code":"CODE"}` |
| POST `/api/v1/character/{id}/useRecipe` | `{"expectedVersion":5,"recipeId":"…","recipeVersion":0,"ingredientIds":["…"],"amount":1}` |
| POST `/api/v1/user/changePassword` | `{"expectedVersion":2,"currentPassword":"…","newPassword":"…"}` |
| POST `/api/v1/user/changeRole?id=…` (admin) | `{"expectedVersion":2,"role":"USER"}` |

Commands return EquipmentView (version, equipped items, one inventory page, currency/items, slots, calculated stats), except user commands, which return a safe UserResponse.
GET `/api/v1/character/{id}/equipment?size=&after=` returns the view; GET `/api/v1/character/{id}/stats` returns only CharacterStats and reads no inventory at all.
Equipment instances are stored one document per item, so the inventory has no length limit and is always read as a cursor page (`size` 1..200, default 50; `after` is the previous page's last UUID, `next == null` ends the list). See [equipment storage](EQUIPMENT_STORAGE.md).
The character's version covers equipment selection, inventory and currencies together. Replaying a successful non-craft command with its old expectedVersion returns 409, preventing duplicate mutations. PoE crafting retains its requestId receipt mechanism.

## Equipment

Slots: HELMET, BODY, GLOVES, BOOTS, BELT, AMULET, RING_LEFT, RING_RIGHT, MAIN_HAND, OFF_HAND, WINGS.
A UUID can occupy only one slot. Equipping another item in an occupied slot replaces its selection; neither item is destroyed.
Two-handed weapons block the off-hand, except bow + quiver. A quiver requires a bow. Shields and one-handed weapons fit the off-hand.
Level and base attribute requirements are validated. A candidate's own bonuses do not satisfy its requirements.
After any equipment/craft transition, the whole loadout is validated. Unequipping a supporting item is rejected if it would invalidate another equipped item's requirements; unequip dependants first.
New instances snapshot their base template. Administrators editing the catalog no longer change those already-issued snapshots.
Only equipped items and the UUID a command addresses are loaded per request, so stat calculation and validation cost the same whether a character owns ten items or a million.
Only selected equipped items contribute stats. Inventory items and previewed templates do not grant bonuses.

## Calculation contract: compact-character-v1

CharacterStatsCalculator is shared by HTTP reads and EquipmentRules validation. PoE rolls are resolved by their stored ID/revision; local and global contributions are processed separately.

- Local flat/percentage physical damage and quality affect only the relevant weapon; local attack speed and accuracy also stay with that weapon.
- Local armour, evasion and energy shield contribute their computed item values to the character.
- Attributes, life/mana, resistance, regeneration, movement, rarity and selected attack/spell modifiers are aggregated from equipped items.
- Generic character-scope `ModifierEffect.Stat` definitions use DefaultStatResolver (flat, additive increased/reduced, multiplicative more/less, SET and bounds). Unsupported contexts/effects and unknown raw stat IDs are returned in `unsupported`.
- Resistance results expose capped and uncapped values; the current cap is 75%, floor -200%.
- Current project starting attributes are 20/20/20, life is `50 + 12*(level-1) + strength/2`, mana is `34 + 6*(level-1) + intelligence/2`, before applicable resource bonuses. Stored supported stock skills also contribute.
- Weapon results include physical range, speed, critical chance, accuracy, average hit and DPS before enemy mitigation, hit chance and critical-hit expectation. Separate hand results are not a claim of full skill/dual-wield combat simulation.
- Base defensive range uses its minimum because historical instances do not store an independent base-defence roll. This policy is explicit rather than re-randomizing on every read.

This completes equipment selection and a shared **character sheet** for the compact catalog. It does not implement a combat simulator, skills, sockets, gems, damage conversion or every custom conditional effect. Unsupported effects are exposed, not silently declared supported.

## Recipe/redemption rules

Redemption checks expiry, prior use and ownership, adds rewards, records redemption and updates the code counter in the same transaction.
Instantaneous recipes (`timeWork=0`, no skill requirements) validate recipeVersion, unlock state, ingredient selectors and integer quantities, debit inputs before adding outputs, and atomically update usage counters. Fractions, negative quantities and unsupported timed/skill-gated recipes fail without consuming items. Timed jobs and skill-gated recipe execution are outside this release.
Administrative stack adjustments reject unknown/deleted items, duplicates, overflow, negative balances and excessive inventory size.
