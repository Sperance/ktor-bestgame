import application.enums.EnumEquipmentType
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import config.EquipmentSeeder
import config.ModifierSeeder
import config.PoolSeeder
import config.RedemptionSeeder
import config.UniqueEquipmentSeeder
import features.data.equipment.equipment_data.Equipment
import features.data.redemptionCodes.RedemptionKind
import features.logic.modifiers.ModifierDefinition
import application.enums.EnumStatStock
import features.logic.pools.EnumPoolTarget
import org.junit.Test

/**
 * Проверки справочных данных сидеров. Mongo не нужна: сидеры - чистые данные.
 */
class SeedDataTest {

    private val definitions: List<ModifierDefinition> =
        ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()

    private val modifierPools = PoolSeeder.table(EnumPoolTarget.MODIFIER)

    private val equipment: List<Equipment> = EquipmentSeeder(definitions).seed()

    @Test
    fun modifier_codes_are_unique() {
        val duplicates = definitions.groupBy { it.code }.filterValues { it.size > 1 }.keys
        assert(duplicates.isEmpty()) { "Duplicate modifier codes: $duplicates" }
    }

    @Test
    fun modifier_ids_are_unique_and_stable_between_seeds() {
        assert(definitions.map { it._id }.toSet().size == definitions.size) { "Duplicate modifier definition ids" }

        val again = ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()
        assert(again.associate { it.code to it._id } == definitions.associate { it.code to it._id }) {
            "Modifier definition ids changed between seeds"
        }
    }

    @Test
    fun every_definition_has_effects_and_tiers() {
        definitions.forEach { definition ->
            assert(definition.effects.isNotEmpty()) { "${definition.code} has no effects" }
            assert(definition.tiers.isNotEmpty()) { "${definition.code} has no tiers" }
        }
    }

    @Test
    fun every_tier_has_a_value_per_effect() {
        definitions.forEach { definition ->
            definition.tiers.forEachIndexed { index, tier ->
                assert(tier.problem(definition.effects.size) == null) { "${definition.code} T${index + 1}: ${tier.problem(definition.effects.size)}" }
            }
        }
    }

    @Test
    fun better_tiers_are_stronger_and_require_higher_item_level() {
        definitions.forEach { definition ->
            definition.tiers.zipWithNext { better, worse ->
                assert(better.level >= worse.level) { "${definition.code}: a better tier requires less item level" }
                better.values.forEachIndexed { index, (_, max) ->
                    // Отрицательный эффект (0.66.0: порча, «меньше получаемого урона») сильнее по модулю
                    val negative = max <= 0.0 && worse.values[index][1] <= 0.0
                    assert(if (negative) -max >= -worse.values[index][1] else max >= worse.values[index][1]) { "${definition.code}: a better tier is weaker" }
                }
            }
        }
    }

    @Test
    fun composite_modifiers_roll_all_their_effects_together() {
        val composite = definitions.filter { it.isComposite() }.flatMap { definition -> definition.tiers.map { definition to it } }
        assert(composite.isNotEmpty()) { "No composite modifiers in seed data" }

        composite.forEach { (definition, tier) ->
            val rolled = tier.roll()
            assert(rolled.size == tier.values.size) { "rolled ${rolled.size} values for ${tier.values.size} effects" }
            rolled.forEachIndexed { index, value ->
                val (min, max) = tier.values[index]
                // to1Digits округляет, поэтому допускаем шаг округления на границах
                assert(value >= min - 0.05 && value <= max + 0.05) { "${definition.code}: $value out of $min..$max" }
            }
        }
    }

    /** Всё, что ссылается на модификатор, ссылается кодом (с 0.56.0): база шаблона, строки, конверсии классов, узлы дерева. */
    @Test
    fun every_modifier_reference_is_a_known_code() {
        val codes = definitions.map { it.code }.toSet()
        val references = equipment.flatMap { item -> item.baseParams.map { it.modifierCode } + item.fixedModifierCodes } +
            config.ProgressionSeeder.seedClasses(definitions).flatMap { it.params }.map { it.modifierCode } +
            config.SkillTreeSeeder.seed(definitions).flatMap { node -> node.params + node.options.flatten() }.map { it.modifierCode }
        assert(references.isNotEmpty() && references.all { it in codes }) { "Unknown references: ${references.filterNot { it in codes }.distinct()}" }
    }

    @Test
    fun equipment_codes_are_unique() {
        val duplicates = equipment.groupBy { it.code }.filterValues { it.size > 1 }.keys
        assert(duplicates.isEmpty()) { "Duplicate equipment codes: $duplicates" }
    }

    @Test
    fun equipment_ids_are_stable_between_seeds() {
        val second = EquipmentSeeder(definitions).seed()
        val first = equipment.associate { it.code to it._id }

        second.forEach { item ->
            assert(first[item.code] == item._id) { "${item.code} got a different _id between seeds" }
        }
    }

    @Test
    fun every_slot_has_at_least_three_uniques() {
        // Уникалки боссов (0.32.0) и кузнеца (0.38.0) - сверх этого: они не состоят в общем пуле. С 0.70.0 общий пул шире трёх.
        val world = PoolSeeder.table(EnumPoolTarget.EQUIPMENT).members("unique:world")
        val bySlot = equipment.filter { it.rarity == EnumRarity.UNIQUE && it.code in world }.groupBy { it.slot }

        // Самоцвет носится не на теле, а в гнезде дерева, и уникальных самоцветов
        // пока нет: их сила должна считаться вместе с деревом, а не отдельно от него.
        wearableSlots.forEach { slot ->
            val items = bySlot[slot].orEmpty()
            assert(items.size >= 3) { "$slot has ${items.size} uniques: ${items.map { it.code }}" }
        }
    }

    @Test
    fun uniques_carry_only_their_own_fixed_modifiers() {
        val definitionsByCode = definitions.associateBy { it.code }

        equipment.filter { it.rarity.fixed }.forEach { item ->
            assert(item.fixedModifierCodes.isNotEmpty()) { "${item.code} has no modifiers" }
            assert(item.modifierPools.isEmpty()) { "${item.code} rolls affixes" }

            item.fixedModifierCodes.forEach { id ->
                val definition = definitionsByCode[id]
                assert(definition != null) { "${item.code} references unknown modifier $id" }
                assert(definition!!.source == EnumModifierSource.UNIQUE) {
                    "${item.code} carries a rollable modifier ${definition.code}"
                }
            }
        }
    }

    /**
     * Сид промокодов проходит те же проверки, что и создание промокода руками.
     *
     * Сидер вставляет коды через тот же insertMany, а тот зовёт validateBeforeInsert:
     * пустой подарок или неположительное количество не отвергаются, а роняют сидинг,
     * то есть старт сервера. Именно это и случилось в 0.19.0, когда награды стали
     * обязательными, а пример с пустым списком остался.
     */
    @Test
    fun seeded_redemption_codes_pass_the_rules_that_guard_insertion() {
        val codes = RedemptionSeeder.seed()
        assert(codes.isNotEmpty()) { "No redemption codes seeded" }

        val duplicates = codes.groupBy { it.code }.filterValues { it.size > 1 }.keys
        assert(duplicates.isEmpty()) { "Duplicate redemption codes: $duplicates" }

        codes.forEach { entry ->
            assert(entry.code.isNotBlank()) { "A seeded redemption code has no code" }
            assert(entry.treasure.isNotEmpty()) { "${entry.code} gives nothing" }
            entry.treasure.forEach { reward ->
                assert(reward.amount > 0) { "${entry.code} has a reward of ${reward.amount}" }
                val needsDocument = reward.kind == RedemptionKind.ITEM || reward.kind == RedemptionKind.EQUIPMENT
                // Идентификаторы предметов и шаблонов сид знать не может, поэтому
                // на них он не ссылается - только опыт и золото.
                assert(!needsDocument) { "${entry.code} names a ${reward.kind} document the seed cannot know" }
            }
        }
    }

    @Test
    fun rollable_items_have_both_prefixes_and_suffixes_available() {
        equipment.filter { !it.rarity.fixed }.forEach { item ->
            val sources = modifierPools.of(definitions, item.modifierPools).map { it.value.source }
            assert(sources.contains(EnumModifierSource.PREFIX)) { "${item.code} has no prefixes to roll" }
            assert(sources.contains(EnumModifierSource.SUFFIX)) { "${item.code} has no suffixes to roll" }
        }
    }

    /**
     * Слоты, в которые надевают шаблоны: самоцвет живёт в гнезде, второе кольцо -
     * это место для кольца, а не вид предмета, а карта (0.35.0) открывает локацию. Фляга (0.69.0)
     * живёт по своим правилам: только обычные базы, свои уникалки и редкой не бывает.
     */
    private val wearableSlots = EnumEquipmentType.entries.filterNot { it.isTool || it.isFlask } - EnumEquipmentType.JEWEL - EnumEquipmentType.RING_2 - EnumEquipmentType.MAP

    @Test
    fun every_slot_has_ordinary_bases_of_every_rarity() {
        val ordinary = equipment.filter { !it.rarity.fixed }.groupBy { it.slot }
        wearableSlots.forEach { slot ->
            val rarities = ordinary[slot].orEmpty().map { it.rarity }.toSet()
            // Администратор выдаёт случайный шаблон выбранной редкости и слота - ни одна пара не пустует
            val missing = listOf(EnumRarity.COMMON, EnumRarity.UNCOMMON, EnumRarity.RARE) - rarities
            assert(missing.isEmpty()) { "$slot has no ordinary base of $missing" }
        }
        assert(equipment.none { it.slot == EnumEquipmentType.RING_2 }) { "RING_2 is a place, not a kind of item" }
    }

    @Test
    fun armour_rolls_only_the_local_defences_its_base_carries() {
        val byCode = definitions.associateBy { it.code }
        // Гибрид «защита и здоровье» или «защита и порог оглушения» (0.56.0) проверяется по своей защите
        val defences = setOf(EnumStatStock.STOCK_ARMOR, EnumStatStock.STOCK_EVASION, EnumStatStock.STOCK_ENERGY_SHIELD)
        equipment.filter { !it.rarity.fixed }.forEach { item ->
            val baseStats = item.baseParams.flatMap { byCode.getValue(it.modifierCode).stats() }.toSet()
            val foreign = modifierPools.of(definitions, item.modifierPools).map { it.value }
                .filter { it.isLocal && it.isNaturalAffix() && !it.tags.orEmpty().contains("weapon") }
                .filterNot { modifier -> baseStats.containsAll(modifier.stats().filter { it in defences }) }
            assert(foreign.isEmpty()) { "${item.code} rolls local modifiers its base does not carry: ${foreign.map { it.code }}" }
        }
    }

    @Test
    fun every_ordinary_base_has_an_icon() {
        val icons = kotlinx.serialization.json.Json.parseToJsonElement(
            javaClass.classLoader.getResource("icons/icons.json")!!.readText()
        ).let { (it as kotlinx.serialization.json.JsonObject)["icons"] as kotlinx.serialization.json.JsonObject }
        val missing = equipment.filter { it.slot != EnumEquipmentType.JEWEL }.map { "equipment.${it.code}" }.filterNot { it in icons }
        assert(missing.isEmpty()) { "Bases without an icon: $missing" }
    }

    /**
     * Каждый рисунок набора - объект `{viewBox, paths}`, и каждый код указывает на существующий (0.69.1):
     * клиент читает набор целиком, и один рисунок не той формы оставлял его без всех иконок.
     */
    @Test
    fun every_sprite_is_a_drawing_and_every_icon_names_one() {
        val document = kotlinx.serialization.json.Json.parseToJsonElement(javaClass.classLoader.getResource("icons/icons.json")!!.readText())
            as kotlinx.serialization.json.JsonObject
        val sprites = document["sprites"] as kotlinx.serialization.json.JsonObject
        val malformed = sprites.filter { (_, sprite) ->
            val drawing = sprite as? kotlinx.serialization.json.JsonObject
            drawing == null || drawing["viewBox"] == null || (drawing["paths"] as? kotlinx.serialization.json.JsonArray).isNullOrEmpty()
        }.keys
        assert(malformed.isEmpty()) { "Sprites that are not a drawing: $malformed" }
        val dangling = (document["icons"] as kotlinx.serialization.json.JsonObject).filterValues { name ->
            (name as kotlinx.serialization.json.JsonPrimitive).content !in sprites
        }.keys
        assert(dangling.isEmpty()) { "Icons naming no sprite: ${dangling.take(10)}" }
    }

    /**
     * Сетка тиров (0.66.0, как в POE): у каждого семейства своё число тиров, но уровни идут строго
     * вниз от лучшего к худшему, дно всегда открыто с первого уровня, а вершина - не выше 68-го,
     * чтобы тир 1 вообще мог выпасть на предмете 71-го уровня.
     */
    @Test
    fun a_natural_affix_follows_a_tier_grid() {
        val natural = modifierPools.tags.filterNot { it == "map" || it.startsWith("tool") }.flatMap { modifierPools.members(it).keys }.toSet()
        definitions.filter { it.isNaturalAffix() && it.code in natural }.forEach { definition ->
            val levels = definition.tiers.map { it.level }
            assert(levels.last() == 1 && levels.first() <= 68 && levels.zipWithNext().all { (a, b) -> a > b }) { "${definition.code}: levels $levels" }
        }
    }

    /** Гибрид «плюс за минус»: цена - последний эффект - одна на всех тирах и никогда не ноль. */
    @Test
    fun a_risk_modifier_keeps_its_price_on_every_tier() {
        definitions.filter { it.code.startsWith("RISK_") }.forEach { risk ->
            val prices = risk.tiers.map { it.values.last() }
            assert(prices.all { it[0] == it[1] && it[1] != 0.0 } && prices.distinct().size == 1) { "${risk.code}: $prices" }
        }
    }

    /** Порча (0.66.0): каждый носимый слот, самоцвет и карта открывают Vaal Orb свой пул, а чистый минус лежит только там. */
    @Test
    fun every_slot_has_a_corruption_pool_and_curses_stay_inside_it() {
        EnumEquipmentType.entries.filter { !it.isTool && it != EnumEquipmentType.RING_2 }.forEach { slot ->
            val pool = modifierPools.of(definitions, listOf(features.logic.pools.Pools.corruption(slot))).map { it.value }
            assert(pool.isNotEmpty() && pool.all { it.source == EnumModifierSource.CORRUPTION }) { "$slot: ${pool.map { it.code }}" }
        }
        val leaked = equipment.flatMap { modifierPools.of(definitions, it.modifierPools) }.map { it.value }.filter { it.source == EnumModifierSource.CORRUPTION }
        assert(leaked.isEmpty()) { "corruption rolls from template pools: ${leaked.map { it.code }}" }
    }

    /** Варианты (0.66.0): все варианты семейства носят его эффекты, и у каждого верстачного есть природный близнец. */
    @Test
    fun variants_share_the_effects_of_their_family() {
        val byCode = definitions.associateBy { it.code }
        definitions.filter { it.variant != application.enums.EnumModifierVariant.NATURAL }.forEach { variant ->
            val family = byCode[variant.family]
            assert(family != null && family.effects == variant.effects && family.variant == application.enums.EnumModifierVariant.NATURAL) { "${variant.code} has no family" }
        }
    }

    @Test
    fun every_rollable_modifier_sits_in_some_pool() {
        val pooled = equipment.flatMap { modifierPools.of(definitions, it.modifierPools) }.map { it.value.code }.toSet()
        val loose = definitions.filter { it.isNaturalAffix() && it.tags.orEmpty().any { tag -> tag in setOf("ailment", "risk") } }
            .filterNot { it.code in pooled }.map { it.code }
        assert(loose.isEmpty()) { "Modifiers no pool rolls: $loose" }
    }

    /**
     * Силы уникалок (0.70.0): у каждой уникалки и мифика от одного до трёх свойств, которых нет больше
     * ни на одном предмете и ни в одном обычном модификаторе, а книга сил читается и описывает их все.
     */
    @Test
    fun every_unique_carries_one_to_three_properties_of_its_own() {
        features.logic.powers.PowerContent.book
        val ordinary = ModifierSeeder.seedDefinitions().flatMap { it.stats() }.map { (it as Enum<*>).name }.toSet()
        val byItem = UniqueEquipmentSeeder.records.associate { record -> record.code to record.lines.flatten().map { it.stat.let { s -> (s as Enum<*>).name } }.toSet() }
        val owners = byItem.values.flatten().groupingBy { it }.eachCount()
        byItem.forEach { (code, stats) ->
            val own = stats.filter { it !in ordinary && owners[it] == 1 && (it.startsWith("POWER_") || it.startsWith("FLASK_")) }
            assert(own.size in 1..3) { "$code has ${own.size} properties of its own: $own" }
        }
    }
}
