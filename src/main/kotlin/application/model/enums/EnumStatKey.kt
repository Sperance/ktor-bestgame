package application.model.enums

import application.model.IntStat

enum class EnumStatKey(val code: String, val description: String) : IntStat {
    LIFE("A1", "Здоровье"),
    MANA("A2", "Мана"),
    RAGE("A3", "Энергия"),

    STR("B1", "Сила"),
    DEX("B2", "Ловкость"),
    INT("B3", "Интеллект"),

    CRIT_CHANCE("C0", "Шанс критического удара"),
    CRIT_DAMAGE("C1", "Критический урон"),
    PHYSICAL_DAMAGE("C2", "Физический урон"),
    MAGICAL_DAMAGE("C3", "Магический урон"),
    ATTACK_SPEED("C4", "Скорость атаки"),

    MAGIC_RESIST("D0", "Сопротивление магии"),
    FIRE_RESIST("D1", "Сопротивление огню"),
    COLD_RESIST("D2", "Сопротивление холоду"),
    LIGHTNING_RESIST("D3", "Сопротивление молнии"),
    CHARM_RESIST("D4", "Сопротивление хаосу"),

    INVENTORY_SIZE("E1", "Размер инвентаря")
}