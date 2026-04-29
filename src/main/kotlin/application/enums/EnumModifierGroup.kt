package application.enums

/**
 * Группа модификатора — на одном предмете может быть только ОДИН мод из каждой группы.
 *
 * Это запрещает, например, одновременно иметь "+50 к здоровью" (LIFE_FLAT_PREFIX)
 * и "+80 к здоровью" (тоже LIFE_FLAT_PREFIX) на одном предмете.
 * Но "+50 к здоровью" и "20% увеличение здоровья" — в разных группах, можно.
 */
enum class EnumModifierGroup(val nameRu: String) {

    // ===== PREFIX groups =====

    LIFE_FLAT_PREFIX("Жизнь (плоская)"),
    LIFE_INC_PREFIX("Жизнь (% увеличение)"),
    MANA_FLAT_PREFIX("Мана (плоская)"),
    MANA_INC_PREFIX("Мана (% увеличение)"),
    ARMOR_FLAT_PREFIX("Броня (плоская)"),
    ARMOR_INC_PREFIX("Броня (% увеличение)"),
    EVASION_INC_PREFIX("Уклонение (% увеличение)"),
    SHIELD_FLAT_PREFIX("Щит (плоский)"),
    SHIELD_INC_PREFIX("Щит (% увеличение)"),

    PHYS_DMG_FLAT_PREFIX("Физ. урон (плоский)"),
    PHYS_DMG_INC_PREFIX("Физ. урон (% увеличение)"),
    FIRE_DMG_INC_PREFIX("Огн. урон (% увеличение)"),
    COLD_DMG_INC_PREFIX("Хол. урон (% увеличение)"),
    LIGHTNING_DMG_INC_PREFIX("Эл. урон (% увеличение)"),
    CHAOS_DMG_INC_PREFIX("Хаос урон (% увеличение)"),
    MAGICAL_DMG_INC_PREFIX("Маг. урон (% увеличение)"),

    ALL_DMG_INC_PREFIX("Весь урон (% увеличение)"),

    // ===== SUFFIX groups =====

    FIRE_RESIST_SUFFIX("Сопр. огня"),
    COLD_RESIST_SUFFIX("Сопр. льда"),
    LIGHTNING_RESIST_SUFFIX("Сопр. молнии"),
    CHAOS_RESIST_SUFFIX("Сопр. хаоса"),
    ALL_RESIST_SUFFIX("Все сопр."),

    ATTACK_SPEED_SUFFIX("Скор. атаки"),
    CAST_SPEED_SUFFIX("Скор. заклинаний"),
    MOVEMENT_SPEED_SUFFIX("Скор. передвижения"),

    CRIT_CHANCE_SUFFIX("Шанс крита"),
    CRIT_DAMAGE_SUFFIX("Крит. урон"),

    STRENGTH_SUFFIX("Сила"),
    AGILITY_SUFFIX("Ловкость"),
    INTELLECT_SUFFIX("Интеллект"),

    HEALTH_REGEN_SUFFIX("Реген. здоровья"),
    MANA_REGEN_SUFFIX("Реген. маны"),
    LEECH_PHYS_SUFFIX("Вампиризм физ."),
    LEECH_ALL_SUFFIX("Вампиризм общий"),

    // ===== IMPLICIT-only groups (cannot appear as explicit) =====

    IMPLICIT_ARMOR("Броня (implicit)"),
    IMPLICIT_EVASION("Уклонение (implicit)"),
    IMPLICIT_SHIELD("Щит (implicit)"),
    IMPLICIT_ATTACK_SPEED("Скор. атаки (implicit)"),
    IMPLICIT_CRIT_CHANCE("Шанс крита (implicit)"),
    IMPLICIT_RESIST("Сопротивление (implicit)"),
    IMPLICIT_MOVEMENT("Скор. передвижения (implicit)"),
    IMPLICIT_LIFE("Жизнь (implicit)"),
    IMPLICIT_MANA("Мана (implicit)"),
}
