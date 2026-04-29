package application.enums

/**
 * Полная таблица всех возможных модификаторов предметов (PoE-стиль).
 *
 * Каждая запись — один уникальный аффикс с:
 *   - stat, modType, group, category — типология аффикса
 *   - displayName — отображаемое название
 *   - t8Min/t8Max — диапазон для тира 8 (самый слабый, itemLevel 1+)
 *   - t1Min/t1Max — диапазон для тира 1 (лучший, itemLevel 75+)
 *   - Промежуточные тиры 2-7 интерполируются в ModifierPool
 *   - weight — относительный вес при случайном выборе
 *   - requiredLevel — мин. уровень персонажа для предмета
 *   - tags — теги для фильтрации (fire, attack, defence...)
 *
 * Тиры и требования к уровню предмета (itemLevel):
 *   T1 >= 75, T2 >= 60, T3 >= 45, T4 >= 35, T5 >= 25, T6 >= 15, T7 >= 5, T8 >= 1
 */
enum class EnumStatModifierHelper(
    val stat: EnumStatHelper,
    val modType: EnumModifierType,
    val group: EnumModifierGroup,
    val category: EnumModifierCategory,
    val displayName: String,
    val t8Min: Double, val t8Max: Double,
    val t1Min: Double, val t1Max: Double,
    val weight: Int = 1000,
    val requiredLevel: Int = 1,
    val tags: List<String> = emptyList()
) {

    // ========================================
    // ПРЕФИКСЫ: ЗАЩИТА
    // ========================================

    HEALTH_FLAT(
        stat = EnumStatHelper.STOCK_HEALTH,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.LIFE_FLAT_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Здоровья",
        t8Min = 5.0, t8Max = 10.0,
        t1Min = 80.0, t1Max = 120.0,
        weight = 1200, requiredLevel = 1,
        tags = listOf("life")
    ),

    HEALTH_INC(
        stat = EnumStatHelper.STOCK_HEALTH,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.LIFE_INC_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Крепкий",
        t8Min = 1.0, t8Max = 3.0,
        t1Min = 20.0, t1Max = 30.0,
        weight = 600, requiredLevel = 10,
        tags = listOf("life")
    ),

    MANA_FLAT(
        stat = EnumStatHelper.STOCK_MANA,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.MANA_FLAT_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Маны",
        t8Min = 5.0, t8Max = 10.0,
        t1Min = 60.0, t1Max = 90.0,
        weight = 1000, requiredLevel = 1,
        tags = listOf("mana")
    ),

    MANA_INC(
        stat = EnumStatHelper.STOCK_MANA,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.MANA_INC_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Мудрый",
        t8Min = 1.0, t8Max = 3.0,
        t1Min = 15.0, t1Max = 25.0,
        weight = 500, requiredLevel = 8,
        tags = listOf("mana")
    ),

    ARMOR_FLAT(
        stat = EnumStatHelper.STOCK_ARMOR,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.ARMOR_FLAT_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Защитный",
        t8Min = 10.0, t8Max = 30.0,
        t1Min = 150.0, t1Max = 300.0,
        weight = 1000, requiredLevel = 1,
        tags = listOf("armor", "defence")
    ),

    ARMOR_INC(
        stat = EnumStatHelper.STOCK_ARMOR,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.ARMOR_INC_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Укреплённый",
        t8Min = 3.0, t8Max = 8.0,
        t1Min = 40.0, t1Max = 60.0,
        weight = 800, requiredLevel = 5,
        tags = listOf("armor", "defence")
    ),

    EVASION_INC(
        stat = EnumStatHelper.STOCK_EVASION,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.EVASION_INC_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Призрачный",
        t8Min = 3.0, t8Max = 8.0,
        t1Min = 40.0, t1Max = 60.0,
        weight = 800, requiredLevel = 5,
        tags = listOf("evasion", "defence")
    ),

    SHIELD_FLAT(
        stat = EnumStatHelper.STOCK_ENERGY_SHIELD,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.SHIELD_FLAT_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Барьерный",
        t8Min = 3.0, t8Max = 8.0,
        t1Min = 50.0, t1Max = 100.0,
        weight = 900, requiredLevel = 1,
        tags = listOf("energy_shield", "defence")
    ),

    SHIELD_INC(
        stat = EnumStatHelper.STOCK_ENERGY_SHIELD,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.SHIELD_INC_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Магический барьер",
        t8Min = 3.0, t8Max = 8.0,
        t1Min = 30.0, t1Max = 60.0,
        weight = 700, requiredLevel = 8,
        tags = listOf("energy_shield", "defence")
    ),

    // ========================================
    // ПРЕФИКСЫ: АТАКА
    // ========================================

    PHYS_DMG_FLAT(
        stat = EnumStatHelper.STOCK_ATTACK_PHYSICAL,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.PHYS_DMG_FLAT_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Кровавый",
        t8Min = 2.0, t8Max = 5.0,
        t1Min = 30.0, t1Max = 60.0,
        weight = 1000, requiredLevel = 1,
        tags = listOf("physical", "attack")
    ),

    PHYS_DMG_INC(
        stat = EnumStatHelper.STOCK_ATTACK_PHYSICAL,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.PHYS_DMG_INC_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Яростный",
        t8Min = 5.0, t8Max = 15.0,
        t1Min = 50.0, t1Max = 80.0,
        weight = 1000, requiredLevel = 1,
        tags = listOf("physical", "attack")
    ),

    FIRE_DMG_INC(
        stat = EnumStatHelper.STOCK_ATTACK_FIRE,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.FIRE_DMG_INC_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Пылающий",
        t8Min = 5.0, t8Max = 15.0,
        t1Min = 40.0, t1Max = 70.0,
        weight = 800, requiredLevel = 10,
        tags = listOf("fire", "elemental")
    ),

    COLD_DMG_INC(
        stat = EnumStatHelper.STOCK_ATTACK_COLD,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.COLD_DMG_INC_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Леденящий",
        t8Min = 5.0, t8Max = 15.0,
        t1Min = 40.0, t1Max = 70.0,
        weight = 800, requiredLevel = 10,
        tags = listOf("cold", "elemental")
    ),

    LIGHTNING_DMG_INC(
        stat = EnumStatHelper.STOCK_ATTACK_LIGHTNING,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.LIGHTNING_DMG_INC_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Громовой",
        t8Min = 5.0, t8Max = 15.0,
        t1Min = 40.0, t1Max = 70.0,
        weight = 800, requiredLevel = 10,
        tags = listOf("lightning", "elemental")
    ),

    CHAOS_DMG_INC(
        stat = EnumStatHelper.STOCK_ATTACK_CHAOS,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.CHAOS_DMG_INC_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Хаотичный",
        t8Min = 3.0, t8Max = 10.0,
        t1Min = 25.0, t1Max = 50.0,
        weight = 600, requiredLevel = 20,
        tags = listOf("chaos")
    ),

    ALL_DMG_MORE(
        stat = EnumStatHelper.STOCK_ATTACK_PHYSICAL,
        modType = EnumModifierType.MORE,
        group = EnumModifierGroup.ALL_DMG_INC_PREFIX,
        category = EnumModifierCategory.PREFIX,
        displayName = "Опустошительный",
        t8Min = 2.0, t8Max = 5.0,
        t1Min = 15.0, t1Max = 25.0,
        weight = 250, requiredLevel = 30,
        tags = listOf("physical", "attack")
    ),

    // ========================================
    // СУФФИКСЫ: СОПРОТИВЛЕНИЯ
    // ========================================

    FIRE_RESIST(
        stat = EnumStatHelper.STOCK_RESIST_FIRE,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.FIRE_RESIST_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Огня",
        t8Min = 5.0, t8Max = 10.0,
        t1Min = 35.0, t1Max = 45.0,
        weight = 1000, requiredLevel = 1,
        tags = listOf("resistance", "fire")
    ),

    COLD_RESIST(
        stat = EnumStatHelper.STOCK_RESIST_COLD,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.COLD_RESIST_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Льда",
        t8Min = 5.0, t8Max = 10.0,
        t1Min = 35.0, t1Max = 45.0,
        weight = 1000, requiredLevel = 1,
        tags = listOf("resistance", "cold")
    ),

    LIGHTNING_RESIST(
        stat = EnumStatHelper.STOCK_RESIST_LIGHTNING,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.LIGHTNING_RESIST_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Молнии",
        t8Min = 5.0, t8Max = 10.0,
        t1Min = 35.0, t1Max = 45.0,
        weight = 1000, requiredLevel = 1,
        tags = listOf("resistance", "lightning")
    ),

    CHAOS_RESIST(
        stat = EnumStatHelper.STOCK_RESIST_CHAOS,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.CHAOS_RESIST_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Хаоса",
        t8Min = 3.0, t8Max = 8.0,
        t1Min = 20.0, t1Max = 35.0,
        weight = 700, requiredLevel = 1,
        tags = listOf("resistance", "chaos")
    ),

    // ========================================
    // СУФФИКСЫ: СКОРОСТЬ
    // ========================================

    ATTACK_SPEED(
        stat = EnumStatHelper.STOCK_ATTACK_SPEED,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.ATTACK_SPEED_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Проворства",
        t8Min = 2.0, t8Max = 5.0,
        t1Min = 15.0, t1Max = 25.0,
        weight = 900, requiredLevel = 1,
        tags = listOf("attack", "speed")
    ),

    CAST_SPEED(
        stat = EnumStatHelper.STOCK_CAST_SPEED,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.CAST_SPEED_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Колдовства",
        t8Min = 2.0, t8Max = 5.0,
        t1Min = 12.0, t1Max = 22.0,
        weight = 900, requiredLevel = 1,
        tags = listOf("spell", "speed")
    ),

    MOVEMENT_SPEED(
        stat = EnumStatHelper.STOCK_MOVEMENT_SPEED,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.MOVEMENT_SPEED_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Быстроногий",
        t8Min = 2.0, t8Max = 5.0,
        t1Min = 15.0, t1Max = 25.0,
        weight = 800, requiredLevel = 1,
        tags = listOf("speed", "movement")
    ),

    // ========================================
    // СУФФИКСЫ: КРИТИЧЕСКИЕ УДАРЫ
    // ========================================

    CRIT_CHANCE(
        stat = EnumStatHelper.STOCK_CRITICAL_CHANCE,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.CRIT_CHANCE_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Рока",
        t8Min = 5.0, t8Max = 15.0,
        t1Min = 30.0, t1Max = 50.0,
        weight = 700, requiredLevel = 1,
        tags = listOf("critical")
    ),

    CRIT_DAMAGE(
        stat = EnumStatHelper.STOCK_CRITICAL_DAMAGE,
        modType = EnumModifierType.INCREASED,
        group = EnumModifierGroup.CRIT_DAMAGE_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Смерти",
        t8Min = 8.0, t8Max = 20.0,
        t1Min = 40.0, t1Max = 70.0,
        weight = 700, requiredLevel = 1,
        tags = listOf("critical")
    ),

    // ========================================
    // СУФФИКСЫ: АТРИБУТЫ
    // ========================================

    STRENGTH(
        stat = EnumStatHelper.STOCK_STRENGTH,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.STRENGTH_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Силача",
        t8Min = 2.0, t8Max = 5.0,
        t1Min = 20.0, t1Max = 35.0,
        weight = 900, requiredLevel = 1,
        tags = listOf("attribute", "strength")
    ),

    AGILITY(
        stat = EnumStatHelper.STOCK_AGILITY,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.AGILITY_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Ловкача",
        t8Min = 2.0, t8Max = 5.0,
        t1Min = 20.0, t1Max = 35.0,
        weight = 900, requiredLevel = 1,
        tags = listOf("attribute", "agility")
    ),

    INTELLECT(
        stat = EnumStatHelper.STOCK_INTELLECT,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.INTELLECT_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Мудреца",
        t8Min = 2.0, t8Max = 5.0,
        t1Min = 20.0, t1Max = 35.0,
        weight = 900, requiredLevel = 1,
        tags = listOf("attribute", "intellect")
    ),

    // ========================================
    // СУФФИКСЫ: РЕГЕНЕРАЦИЯ И ВАМПИРИЗМ
    // ========================================

    HEALTH_REGEN(
        stat = EnumStatHelper.STOCK_HEALTH_REGEN,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.HEALTH_REGEN_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Исцеления",
        t8Min = 1.0, t8Max = 3.0,
        t1Min = 10.0, t1Max = 20.0,
        weight = 800, requiredLevel = 1,
        tags = listOf("life", "regen")
    ),

    MANA_REGEN(
        stat = EnumStatHelper.STOCK_MANA_REGEN,
        modType = EnumModifierType.FLAT_ADD,
        group = EnumModifierGroup.MANA_REGEN_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Восстановления",
        t8Min = 1.0, t8Max = 3.0,
        t1Min = 8.0, t1Max = 15.0,
        weight = 700, requiredLevel = 1,
        tags = listOf("mana", "regen")
    ),

    LEECH_PHYS(
        stat = EnumStatHelper.STOCK_LEECH_PHYSICAL,
        modType = EnumModifierType.LEECH,
        group = EnumModifierGroup.LEECH_PHYS_SUFFIX,
        category = EnumModifierCategory.SUFFIX,
        displayName = "Кровопийцы",
        t8Min = 0.1, t8Max = 0.3,
        t1Min = 1.0, t1Max = 2.5,
        weight = 400, requiredLevel = 15,
        tags = listOf("leech", "life", "physical")
    );

    companion object {
        val prefixes = entries.filter { it.category == EnumModifierCategory.PREFIX }
        val suffixes = entries.filter { it.category == EnumModifierCategory.SUFFIX }
    }
}
