package com.sperance.exileforge.rules.content

/*
 * Коды статов карт и атласа, которые движок называет по имени (1.1.0). Строкой их больше не пишут:
 * имя - элемент перечисления, и [StatRegistry.validate] при загрузке проверяет, что каждый есть в
 * `stats.json`. Опечатка вроде `MAP_PACK` вместо `MAP_PACK_SIZE` (1.0.1) или `ATLAS_RARE_MODS`
 * вместо `ATLAS_MONSTER_MODS` роняет старт, а не молча обнуляет строку.
 */

/** Строки карт, которые читает движок: `MAP_<имя>`. */
enum class MapStat {
    ABYSS_CRACKS, ABYSS_DAMAGE, ABYSS_DEPTH, ABYSS_HOARD, ABYSS_LEADER, ABYSS_LIFE, ABYSS_ORBS, ABYSS_RARE, ABYSS_SWARM,
    ABYSS_UNIQUE, BOOKS, BOSS_POWER, CHESTS, CRYSTALS, EXPERIENCE, FLASK_CHARGES, FOUNTAINS, GOLD, HERO_ATTACK_SPEED,
    HERO_BLOCK, HERO_BUFF_DURATION, HERO_CRIT, HERO_DAMAGE_TAKEN, HERO_DEGEN, HERO_DEFENCES, HERO_ENFEEBLE, HERO_FLASK_EFFECT,
    HERO_HASTE, HERO_LEECH, HERO_LIFE, HERO_LIGHT,
    HERO_MANA_REGEN, HERO_MAX_RESIST, HERO_RECOVERY, HERO_REGEN, HERO_RESIST, HERO_SLOW, HERO_VULNERABILITY, ITEM_LEVEL, MAGIC_MONSTERS,
    MONSTER_AILMENTS, MONSTER_ARMOUR, MONSTER_CAST, MONSTER_CRITICAL, MONSTER_DAMAGE, MONSTER_EXTRA_ELEMENTAL, MONSTER_FORTIFY,
    MONSTER_LEECH, MONSTER_LIFE, MONSTER_MAGIC_MIN, MONSTER_ONSLAUGHT, MONSTER_PENETRATION, MONSTER_RARITY, MONSTER_REFLECT,
    MONSTER_RESIST, MONSTER_SPEED,
    MONSTER_STUN, PACK_SIZE, QUANTITY, RARE_MONSTERS, RARITY, SKILL_COST;

    val code: String = "MAP_$name"
}

/** Бонусы атласа, которые читает движок: `ATLAS_<имя>`. */
enum class AtlasStat {
    ABYSS_CHANCE, ABYSS_DEPTH, ABYSS_EXTRA, ABYSS_HOARD, ABYSS_ORBS, ABYSS_POWER, ABYSS_RARE, ABYSS_UNIQUE,
    BOOKS, BOOKS_OWN, BOSS_LOOT, BOSS_RESPAWN, BOSS_UNIQUE, CHESTS, CHEST_LOOT, CRYSTALS, CRYSTALS_MORE, CRYSTAL_CHANCE,
    CRYSTAL_ESSENCES, CRYSTAL_TIER, EXPERIENCE, FLASK_CHARGES, FLASK_DURATION, FLASK_RARE, FOUNTAINS, GOLD,
    GUARDIAN_POWER, MANA_REGEN, MAP_AFFIX, MAP_DROP, MAP_EFFECT, MAP_NEXT, MAP_RARE, MONSTER_MODS, PACK_SIZE, QUANTITY,
    RARE_MONSTERS, RARITY, RECIPE, SKILL_LEVEL, VAAL_CHANCE, VAAL_MIN_MODS, VAAL_REWARD, VAAL_UNIQUE,
    /** Ремесло (1.41.0): проценты к шансу знамений, катализаторов и сфер качества в добыче, шанс скрытой строки на добыче монстра. */
    OMENS, CATALYSTS, QUALITY_ORBS, VEILED,
    /** Силы монстров (1.41.0): проценты к здоровью, урону и скорости монстров карт - строками карты, с их наградой за риск. */
    MONSTER_LIFE, MONSTER_DAMAGE, MONSTER_SPEED,
    /** Тиры карт (1.41.0): проценты к шансу упавшей карты подняться на ступень. */
    MAP_TIER;

    val code: String = "ATLAS_$name"
}
