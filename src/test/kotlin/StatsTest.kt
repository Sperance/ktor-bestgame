import application.enums.EnumModifierOperation.ADD
import application.enums.EnumModifierOperation.INCREASED
import application.enums.EnumStatStock.STOCK_AGILITY
import application.enums.EnumStatStock.STOCK_ARMOR
import application.enums.EnumStatStock.STOCK_EVASION
import application.enums.EnumStatStock.STOCK_HEALTH
import application.enums.EnumStatStock.STOCK_INTELLECT
import application.enums.EnumStatStock.STOCK_STRENGTH
import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import config.ModifierSeeder
import config.ProgressionSeeder
import config.UniqueEquipmentSeeder
import features.data.equipment.equipment_data.Armor
import features.logic.modifiers.ModifierCalculator
import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.StatOperation
import features.logic.stats.EquipmentRequirements
import org.junit.Test

/**
 * Расчёт характеристик: конверсии, порядок статов, требования предметов.
 * Mongo не нужна - compute работает на уже развёрнутых операциях.
 */
class StatsTest {

    private val definitions: List<ModifierDefinition> =
        ModifierSeeder.seedDefinitions() + UniqueEquipmentSeeder.seedDefinitions()

    // ==================== Конверсии ====================

    @Test
    fun conversion_reads_the_already_computed_source() {
        // +1 к здоровью за каждые 2 Силы, Сила 20 -> +10 здоровья
        val result = ModifierCalculator.compute(
            base = mapOf(STOCK_STRENGTH to 20.0, STOCK_HEALTH to 50.0),
            operations = listOf(StatOperation(STOCK_HEALTH, ADD, 1.0, perStat = STOCK_STRENGTH, perAmount = 2.0))
        )
        assert(result[STOCK_HEALTH] == 60.0) { "expected 60.0, got ${result[STOCK_HEALTH]}" }
    }

    @Test
    fun conversion_sees_bonuses_added_to_its_source() {
        // Сила 20 базы плюс 30 с предмета -> конверсия считает от 50
        val result = ModifierCalculator.compute(
            base = mapOf(STOCK_STRENGTH to 20.0, STOCK_HEALTH to 50.0),
            operations = listOf(
                StatOperation(STOCK_STRENGTH, ADD, 30.0),
                StatOperation(STOCK_HEALTH, ADD, 1.0, perStat = STOCK_STRENGTH, perAmount = 2.0)
            )
        )
        assert(result[STOCK_STRENGTH] == 50.0)
        assert(result[STOCK_HEALTH] == 75.0) { "expected 75.0, got ${result[STOCK_HEALTH]}" }
    }

    @Test
    fun conversion_result_is_amplified_by_percentages() {
        // (50 базы + 10 от Силы) * 1.5 = 90 - конверсия входит в базовую часть, как в POE
        val result = ModifierCalculator.compute(
            base = mapOf(STOCK_STRENGTH to 20.0, STOCK_HEALTH to 50.0),
            operations = listOf(
                StatOperation(STOCK_HEALTH, ADD, 1.0, perStat = STOCK_STRENGTH, perAmount = 2.0),
                StatOperation(STOCK_HEALTH, INCREASED, 50.0)
            )
        )
        assert(result[STOCK_HEALTH] == 90.0) { "expected 90.0, got ${result[STOCK_HEALTH]}" }
    }

    @Test
    fun an_incomplete_step_does_not_count() {
        // 9 Силы при шаге 2 дают 4 применения, а не 4.5
        val result = ModifierCalculator.compute(
            base = mapOf(STOCK_STRENGTH to 9.0),
            operations = listOf(StatOperation(STOCK_HEALTH, ADD, 1.0, perStat = STOCK_STRENGTH, perAmount = 2.0))
        )
        assert(result[STOCK_HEALTH] == 4.0) { "expected 4.0, got ${result[STOCK_HEALTH]}" }
    }

    @Test
    fun a_missing_source_converts_to_nothing() {
        val result = ModifierCalculator.compute(
            base = emptyMap(),
            operations = listOf(StatOperation(STOCK_HEALTH, ADD, 5.0, perStat = STOCK_STRENGTH, perAmount = 2.0))
        )
        assert(result[STOCK_HEALTH] == 0.0) { "expected 0.0, got ${result[STOCK_HEALTH]}" }
    }

    // ==================== Порядок статов ====================

    @Test
    fun stat_order_is_unique_inside_every_enum() {
        listOf(
            application.enums.EnumStatStock.entries,
            application.enums.EnumStatBool.entries,
            application.enums.EnumStatProfession.entries,
            application.enums.EnumStatBattle.entries,
        ).forEach { group ->
            val orders = group.map { it.order }
            assert(orders.toSet().size == orders.size) { "Duplicate order inside ${group.first()::class.simpleName}" }
        }
    }

    @Test
    fun attributes_are_computed_before_what_they_convert_into() {
        listOf(STOCK_STRENGTH, STOCK_AGILITY, STOCK_INTELLECT).forEach { attribute ->
            listOf(STOCK_HEALTH, STOCK_EVASION, STOCK_ARMOR).forEach { derived ->
                assert(attribute.order < derived.order) { "$attribute is computed after $derived" }
            }
        }
    }

    @Test
    fun every_seeded_conversion_points_forward() {
        definitions.forEach { definition ->
            definition.effects.forEach { effect ->
                val source = effect.perStat ?: return@forEach
                assert(source.order < effect.stat.order) {
                    "${definition.code}: $source(${source.order}) -> ${effect.stat}(${effect.stat.order})"
                }
                assert(effect.perAmount > 0.0) { "${definition.code}: step ${effect.perAmount}" }
            }
        }
    }

    @Test
    fun no_local_modifier_is_a_conversion() {
        definitions.filter { it.isLocal }.forEach { definition ->
            assert(definition.effects.none { it.isConversion() }) {
                "${definition.code} is local and would pull a global stat inside the item"
            }
        }
    }

    @Test
    fun item_pools_roll_local_defences_only() {
        // Глобальные версии защитных статов остались для дерева и не должны роллиться
        val rollable = definitions.filter {
            it.source == application.enums.EnumModifierSource.PREFIX ||
                it.source == application.enums.EnumModifierSource.SUFFIX
        }
        val globalDefence = rollable.filterNot { it.isLocal }.filter { definition ->
            definition.effects.any { it.stat == STOCK_ARMOR || it.stat == STOCK_EVASION }
        }
        assert(globalDefence.isEmpty()) { "Global defence mods can be rolled: ${globalDefence.map { it.code }}" }
    }

    // ==================== Требования ====================

    private fun helm(level: Int, strength: Int) = Armor(
        slot = EnumEquipmentType.HELMET,
        name = "Test Helm",
        rarity = EnumRarity.COMMON,
        itemLevel = level,
        requiredLevel = level,
        requiredStrength = strength
    )

    @Test
    fun requirements_pass_when_the_character_is_strong_enough() {
        val unmet = EquipmentRequirements.unmet(helm(10, 30), level = 12, stats = mapOf(STOCK_STRENGTH to 40.0))
        assert(unmet.isEmpty()) { "unexpected: $unmet" }
    }

    @Test
    fun requirements_report_every_missing_condition() {
        val unmet = EquipmentRequirements.unmet(helm(30, 60), level = 12, stats = mapOf(STOCK_STRENGTH to 40.0))
        assert(unmet.map { it.name }.toSet() == setOf("level", "strength")) { "got $unmet" }
        assert(unmet.first { it.name == "strength" }.actual == 40)
    }

    // ==================== Прогрессия ====================

    @Test
    fun class_base_grows_with_level() {
        val marauder = ProgressionSeeder.seedClasses(definitions).first { it.code == "MARAUDER" }

        val first = marauder.baseOn(1)
        val tenth = marauder.baseOn(10)

        assert(first[STOCK_STRENGTH] == 32.0) { "got ${first[STOCK_STRENGTH]}" }
        assert(tenth[STOCK_STRENGTH] == 32.0) { "attributes must not grow on their own" }
        assert(tenth.getValue(STOCK_HEALTH) > first.getValue(STOCK_HEALTH)) { "life must grow with level" }
    }

    @Test
    fun every_class_carries_the_attribute_conversions() {
        val classes = ProgressionSeeder.seedClasses(definitions)
        val byId = definitions.associateBy { it._id }

        assert(classes.size == 3) { "expected three classes, got ${classes.size}" }
        classes.forEach { characterClass ->
            val conversions = characterClass.params.count { byId.getValue(it.modifierId).effects.any { e -> e.isConversion() } }
            assert(conversions > 0) { "${characterClass.code} has no attribute conversions" }
        }
    }

    @Test
    fun experience_table_grows_and_hands_out_points() {
        val levels = ProgressionSeeder.seedLevels().sortedBy { it.level }

        assert(levels.first().level == 1 && levels.first().experience == 0.0) { "level 1 must start at zero" }
        levels.zipWithNext { lower, higher ->
            assert(higher.experience > lower.experience) { "level ${higher.level} is not harder than ${lower.level}" }
        }
        assert(levels.sumOf { it.skillPoints } == levels.size + 1) { "each level past the first gives one point" }
    }
}
