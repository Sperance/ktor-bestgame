package ru.descend.domain.icons

import ru.descend.domain.enums.EnumEquipmentType
import ru.descend.domain.enums.EnumEquipmentWeapon
import ru.descend.domain.enums.EnumRarity
import ru.descend.domain.modifiers.ModifierDefinition
import ru.descend.domain.modifiers.ModifierEffect
import ru.descend.domain.modifiers.ModifierSource

/**
 * Подбор иконки для сущности игры.
 *
 * Правила детерминированы и не зависят от базы данных: одинаковый вход всегда
 * даёт один и тот же идентификатор иконки, поэтому таблицы соответствий можно
 * отдать клиенту один раз и кэшировать вместе с набором картинок.
 *
 * Ничего не угадывается «похоже по смыслу»: сначала точное совпадение по stat id,
 * затем разбор идентификатора по частям, затем теги, затем источник модификатора.
 * Если не подошло ничего — честная заглушка [IconCatalog.FALLBACK], а не случайная картинка.
 */
object IconResolver {

    /** Точные соответствия для stat id импортированного каталога и собственных статов сервера. */
    val stats: Map<String, String> = mapOf(
        "additional_strength" to "stat-strength",
        "additional_dexterity" to "stat-dexterity",
        "additional_intelligence" to "stat-intelligence",
        "additional_all_attributes" to "stat-attributes",
        "strength" to "stat-strength",
        "dexterity" to "stat-dexterity",
        "intelligence" to "stat-intelligence",
        "base_maximum_life" to "stat-life",
        "maximum_life" to "stat-life",
        "base_maximum_mana" to "stat-mana",
        "maximum_mana" to "stat-mana",
        "energy_shield" to "stat-energy-shield",
        "local_energy_shield" to "stat-energy-shield",
        "armour" to "stat-armour",
        "local_base_physical_damage_reduction_rating" to "stat-armour",
        "local_physical_damage_reduction_rating_+%" to "stat-armour",
        "evasion" to "stat-evasion",
        "local_base_evasion_rating" to "stat-evasion",
        "block_chance" to "stat-block",
        "accuracy" to "stat-accuracy",
        "local_accuracy_rating" to "stat-accuracy",
        "accuracy_rating_+%" to "stat-accuracy",
        "local_attack_speed_+%" to "stat-attack-speed",
        "attack_speed_multiplier" to "stat-attack-speed",
        "cast_speed_+%" to "stat-cast-speed",
        "base_movement_velocity_+%" to "stat-movement-speed",
        "critical_strike_chance_+%" to "stat-critical",
        "critical_chance_multiplier" to "stat-critical",
        "base_critical_strike_multiplier_+%" to "stat-critical",
        "local_minimum_added_physical_damage" to "stat-physical-damage",
        "local_maximum_added_physical_damage" to "stat-physical-damage",
        "attack_minimum_added_physical_damage" to "stat-physical-damage",
        "attack_maximum_added_physical_damage" to "stat-physical-damage",
        "local_physical_damage_+%" to "stat-physical-damage",
        "attack_minimum_added_fire_damage" to "stat-fire-damage",
        "attack_maximum_added_fire_damage" to "stat-fire-damage",
        "attack_minimum_added_cold_damage" to "stat-cold-damage",
        "attack_maximum_added_cold_damage" to "stat-cold-damage",
        "attack_minimum_added_lightning_damage" to "stat-lightning-damage",
        "attack_maximum_added_lightning_damage" to "stat-lightning-damage",
        "spell_and_attack_minimum_added_lightning_damage" to "stat-lightning-damage",
        "spell_and_attack_maximum_added_lightning_damage" to "stat-lightning-damage",
        "attack_minimum_added_chaos_damage" to "stat-chaos-damage",
        "attack_maximum_added_chaos_damage" to "stat-chaos-damage",
        "spell_damage_+%" to "stat-spell-damage",
        "damage_+%" to "stat-damage",
        "attack_damage_multiplier" to "stat-damage",
        "elemental_damage_with_attack_skills_+%" to "stat-elemental-damage",
        "base_fire_damage_resistance_%" to "stat-fire-resistance",
        "fire_resistance" to "stat-fire-resistance",
        "base_cold_damage_resistance_%" to "stat-cold-resistance",
        "cold_resistance" to "stat-cold-resistance",
        "base_lightning_damage_resistance_%" to "stat-lightning-resistance",
        "lightning_resistance" to "stat-lightning-resistance",
        "base_chaos_damage_resistance_%" to "stat-chaos-resistance",
        "chaos_resistance" to "stat-chaos-resistance",
        "base_resist_all_elements_%" to "stat-all-resistance",
        "base_life_regeneration_rate_per_minute" to "stat-life-regeneration",
        "life_regeneration" to "stat-life-regeneration",
        "mana_regeneration_rate_+%" to "stat-mana-regeneration",
        "mana_regeneration" to "stat-mana-regeneration",
        "base_item_found_rarity_+%" to "stat-rarity",
        "base_item_found_quantity_+%" to "stat-quantity",
        "global_hit_causes_monster_flee_%" to "combat-flee"
    )

    /** Разбор незнакомого stat id по частям. Порядок важен: сначала точнее, потом общее. */
    private val statParts: List<Pair<Regex, String>> = listOf(
        Regex("resist.*all|all.*resist|all_elements") to "stat-all-resistance",
        Regex("fire.*resist|resist.*fire") to "stat-fire-resistance",
        Regex("cold.*resist|resist.*cold") to "stat-cold-resistance",
        Regex("lightning.*resist|resist.*lightning") to "stat-lightning-resistance",
        Regex("chaos.*resist|resist.*chaos") to "stat-chaos-resistance",
        Regex("life.*regen|regen.*life") to "stat-life-regeneration",
        Regex("mana.*regen|regen.*mana") to "stat-mana-regeneration",
        Regex("leech") to "stat-leech",
        Regex("energy_shield") to "stat-energy-shield",
        Regex("movement|velocity") to "stat-movement-speed",
        Regex("attack_speed") to "stat-attack-speed",
        Regex("cast_speed") to "stat-cast-speed",
        Regex("critical") to "stat-critical",
        Regex("accuracy") to "stat-accuracy",
        Regex("stun") to "stat-stun",
        Regex("block") to "stat-block",
        Regex("fire") to "stat-fire-damage",
        Regex("cold|freeze|chill") to "stat-cold-damage",
        Regex("lightning|shock") to "stat-lightning-damage",
        Regex("chaos|poison") to "stat-chaos-damage",
        Regex("elemental") to "stat-elemental-damage",
        Regex("spell|caster") to "stat-spell-damage",
        Regex("damage_reduction|armour|armor") to "stat-armour",
        Regex("physical") to "stat-physical-damage",
        Regex("evasion|dodge") to "stat-evasion",
        Regex("attribute") to "stat-attributes",
        Regex("strength") to "stat-strength",
        Regex("dexterity") to "stat-dexterity",
        Regex("intelligence") to "stat-intelligence",
        Regex("rarity") to "stat-rarity",
        Regex("quantity") to "stat-quantity",
        Regex("experience") to "stat-experience",
        Regex("gold|money") to "stat-gold",
        Regex("aura") to "stat-aura",
        Regex("curse") to "stat-curse",
        Regex("mana") to "stat-mana",
        Regex("life|health") to "stat-life",
        Regex("damage") to "stat-damage"
    )

    /** Теги каталога. Используются, когда stat id ничего не сказал. */
    val tags: Map<String, String> = mapOf(
        "fire" to "stat-fire-damage", "cold" to "stat-cold-damage", "lightning" to "stat-lightning-damage",
        "chaos" to "stat-chaos-damage", "chaos_damage" to "stat-chaos-damage",
        "elemental" to "stat-elemental-damage", "elemental_damage" to "stat-elemental-damage",
        "physical" to "stat-physical-damage", "physical_damage" to "stat-physical-damage",
        "caster" to "stat-spell-damage", "caster_damage" to "stat-spell-damage",
        "critical" to "stat-critical", "speed" to "stat-attack-speed", "attack" to "stat-damage",
        "damage" to "stat-damage", "armour" to "stat-armour", "defences" to "stat-armour",
        "evasion" to "stat-evasion", "energy_shield" to "stat-energy-shield", "block" to "stat-block",
        "life" to "stat-life", "flat_life_regen" to "stat-life-regeneration", "mana" to "stat-mana",
        "resistance" to "stat-all-resistance", "attribute" to "stat-attributes",
        "resource" to "stat-life", "drop" to "stat-rarity", "gem" to "item-gem", "jewellery" to "jewellery-ring"
    )

    /** Источник модификатора: последний рубеж, когда содержание не распозналось. */
    val sources: Map<ModifierSource, String> = mapOf(
        ModifierSource.BASE_ITEM to "affix-implicit",
        ModifierSource.PREFIX to "affix-prefix",
        ModifierSource.SUFFIX to "affix-suffix",
        ModifierSource.UNIQUE to "affix-unique",
        ModifierSource.ENCHANTMENT to "affix-enchantment",
        ModifierSource.CORRUPTION to "affix-corrupted",
        ModifierSource.PASSIVE to "passive-notable",
        ModifierSource.SKILL to "stat-spell-damage",
        ModifierSource.AURA to "stat-aura",
        ModifierSource.FLASK to "item-flask",
        ModifierSource.JEWEL to "item-gem",
        ModifierSource.MAP to "item-map",
        ModifierSource.MONSTER to "combat-monster",
        ModifierSource.TEMPORARY to "stat-energy",
        ModifierSource.SYSTEM to "ui-unknown"
    )

    /** Классы предметов импортированного каталога. */
    val itemClasses: Map<String, String> = mapOf(
        "One Hand Sword" to "weapon-sword", "Thrusting One Hand Sword" to "weapon-thrusting-sword",
        "Two Hand Sword" to "weapon-two-hand-sword", "One Hand Axe" to "weapon-axe",
        "Two Hand Axe" to "weapon-two-hand-axe", "One Hand Mace" to "weapon-mace",
        "Two Hand Mace" to "weapon-two-hand-mace", "Sceptre" to "weapon-sceptre",
        "Staff" to "weapon-staff", "Warstaff" to "weapon-warstaff", "Bow" to "weapon-bow",
        "Wand" to "weapon-wand", "Claw" to "weapon-claw", "Dagger" to "weapon-dagger",
        "Rune Dagger" to "weapon-rune-dagger",
        "Helmet" to "armour-helmet", "Body Armour" to "armour-body", "Gloves" to "armour-gloves",
        "Boots" to "armour-boots", "Shield" to "armour-shield", "Quiver" to "armour-quiver",
        "Belt" to "armour-belt", "Ring" to "jewellery-ring", "Amulet" to "jewellery-amulet",
        "StackableCurrency" to "stat-gold", "Currency" to "stat-gold",
        "Life Flask" to "item-flask", "Mana Flask" to "item-flask", "Hybrid Flask" to "item-flask",
        "Utility Flask" to "item-flask", "Critical Utility Flask" to "item-flask",
        "Map" to "item-map", "Map Fragment" to "item-map", "Jewel" to "item-gem",
        "Abyss Jewel" to "item-gem", "Active Skill Gem" to "item-gem", "Support Skill Gem" to "item-gem"
    )

    /** Сферы по отображаемому имени: домен не знает о enum валют, но знает их названия. */
    val currencyNames: Map<String, String> = mapOf(
        "orb of transmutation" to "currency-transmutation", "orb of augmentation" to "currency-augmentation",
        "orb of alteration" to "currency-alteration", "orb of alchemy" to "currency-alchemy",
        "chaos orb" to "currency-chaos", "regal orb" to "currency-regal", "exalted orb" to "currency-exalted",
        "orb of scouring" to "currency-scouring", "orb of annulment" to "currency-annulment",
        "divine orb" to "currency-divine", "blessed orb" to "currency-blessed",
        "fracturing orb" to "currency-fracturing", "mirror of kalandra" to "currency-mirror"
    )

    val weapons: Map<EnumEquipmentWeapon, String> = mapOf(
        EnumEquipmentWeapon.SWORD to "weapon-sword", EnumEquipmentWeapon.LONGSWORD to "weapon-longsword",
        EnumEquipmentWeapon.DOUBLESWORD to "weapon-two-hand-sword", EnumEquipmentWeapon.THRUSTING_SWORD to "weapon-thrusting-sword",
        EnumEquipmentWeapon.BLADE to "weapon-blade", EnumEquipmentWeapon.AXE to "weapon-axe",
        EnumEquipmentWeapon.DOUBLEAXE to "weapon-two-hand-axe", EnumEquipmentWeapon.MACE to "weapon-mace",
        EnumEquipmentWeapon.TWO_HAND_MACE to "weapon-two-hand-mace", EnumEquipmentWeapon.SCEPTRE to "weapon-sceptre",
        EnumEquipmentWeapon.STAFF to "weapon-staff", EnumEquipmentWeapon.WARSTAFF to "weapon-warstaff",
        EnumEquipmentWeapon.BOW to "weapon-bow", EnumEquipmentWeapon.WAND to "weapon-wand",
        EnumEquipmentWeapon.CLAW to "weapon-claw", EnumEquipmentWeapon.DAGGER to "weapon-dagger",
        EnumEquipmentWeapon.RUNE_DAGGER to "weapon-rune-dagger"
    )

    val slots: Map<EnumEquipmentType, String> = mapOf(
        EnumEquipmentType.HELMET to "armour-helmet", EnumEquipmentType.BODY to "armour-body",
        EnumEquipmentType.GLOVES to "armour-gloves", EnumEquipmentType.BOOTS to "armour-boots",
        EnumEquipmentType.SHIELD to "armour-shield", EnumEquipmentType.QUIVER to "armour-quiver",
        EnumEquipmentType.BELT to "armour-belt", EnumEquipmentType.WINGS to "armour-wings",
        EnumEquipmentType.RING to "jewellery-ring", EnumEquipmentType.AMULET to "jewellery-amulet",
        EnumEquipmentType.WEAPON_1H to "weapon-sword", EnumEquipmentType.WEAPON_2H to "weapon-two-hand-sword"
    )

    val rarities: Map<EnumRarity, String> = mapOf(
        EnumRarity.COMMON to "rarity-normal", EnumRarity.UNCOMMON to "rarity-magic",
        EnumRarity.RARE to "rarity-rare", EnumRarity.EPIC to "rarity-epic",
        EnumRarity.LEGENDARY to "rarity-unique", EnumRarity.MYTHICAL to "rarity-mythical"
    )

    fun forStat(statId: String): String? {
        val key = statId.lowercase()
        stats[key]?.let { return it }
        return statParts.firstOrNull { it.first.containsMatchIn(key) }?.second
    }

    fun forTag(tag: String): String? = tags[tag.lowercase()]

    fun forItemClass(itemClass: String): String? = itemClasses[itemClass]

    fun forCurrencyName(name: String): String? = currencyNames[name.lowercase()]

    /**
     * Иконка базового предмета каталога.
     *
     * Валюты узнаются по названию, остальное — по классу предмета, а если класс незнаком,
     * последними идут теги записи.
     */
    fun forBaseItem(itemClass: String, name: String, tags: Collection<String> = emptyList()): String {
        if (itemClass == "StackableCurrency" || itemClass == "Currency") forCurrencyName(name)?.let { return it }
        forItemClass(itemClass)?.let { return it }
        return tags.firstNotNullOfOrNull { forTag(it) } ?: "item-generic"
    }

    fun forWeapon(weapon: EnumEquipmentWeapon): String = weapons[weapon] ?: "weapon-sword"

    fun forSlot(slot: EnumEquipmentType): String = slots[slot] ?: "item-generic"

    fun forRarity(rarity: EnumRarity): String = rarities[rarity] ?: "rarity-normal"

    /**
     * Иконка модификатора: сначала его характеристики, затем теги, затем источник.
     *
     * [rawStatIds] — идентификаторы статов исходной записи каталога, включая те,
     * которые сервер ещё не умеет считать: иконка у них есть, а расчёта нет.
     */
    fun forModifier(rawStatIds: List<String>, tags: Collection<String>, source: ModifierSource?): String {
        rawStatIds.firstNotNullOfOrNull { forStat(it) }?.let { return it }
        tagPriority.firstNotNullOfOrNull { priority -> tags.firstOrNull { it.equals(priority, true) }?.let(::forTag) }?.let { return it }
        tags.firstNotNullOfOrNull { forTag(it) }?.let { return it }
        return source?.let { sources[it] } ?: IconCatalog.FALLBACK
    }

    /** Конкретные теги важнее общих: «огонь» говорит больше, чем «урон» или «ресурс». */
    private val tagPriority = listOf("fire", "cold", "lightning", "chaos_damage", "chaos", "physical_damage",
        "elemental_damage", "caster_damage", "critical", "energy_shield", "evasion", "armour", "life", "mana",
        "resistance", "attribute", "speed", "elemental", "physical", "defences", "damage")

    /** Иконка готового определения модификатора, в том числе опубликованного вручную. */
    fun forDefinition(definition: ModifierDefinition): String {
        val statIds = definition.effects.mapNotNull {
            when (it) {
                is ModifierEffect.Stat -> it.stat.value
                is ModifierEffect.DerivedStat -> it.targetStat.value
                is ModifierEffect.Penetration -> it.damageType
                is ModifierEffect.DamageConversion -> it.to
                is ModifierEffect.DamageTakenAs -> it.to
                is ModifierEffect.GainResource -> it.resource
                else -> null
            }
        } + definition.unsupportedStats + listOf(definition.id, definition.name)
        return forModifier(statIds, definition.tags.map { it.value }, definition.source)
    }
}
