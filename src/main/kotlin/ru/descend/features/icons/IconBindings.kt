package ru.descend.features.icons

import ru.descend.domain.icons.IconCatalog
import ru.descend.domain.icons.IconResolver
import ru.descend.features.combat.domain.BattleAction
import ru.descend.features.combat.domain.BattleView
import ru.descend.features.combat.domain.CombatCatalog
import ru.descend.features.combat.domain.BattleStatus
import ru.descend.features.combat.domain.Monster
import ru.descend.features.combat.domain.Zone
import ru.descend.features.equipment.model.Accessory
import ru.descend.features.equipment.model.Armor
import ru.descend.features.equipment.model.Equipment
import ru.descend.features.equipment.model.Weapon
import ru.descend.features.items.model.Items
import ru.descend.features.passives.model.PassiveNode
import ru.descend.features.passives.model.PassiveTree
import ru.descend.features.passives.model.PassiveNodeKind
import ru.descend.features.poe.catalog.PoeCatalog
import ru.descend.features.poe.catalog.string
import ru.descend.features.poe.catalog.strings
import ru.descend.features.poe.catalog.objects
import ru.descend.features.poe.domain.PoeCurrency
import ru.descend.features.poe.domain.PoeRarity
import kotlinx.serialization.json.JsonObject

/**
 * Привязка иконок к сущностям сервера.
 *
 * Здесь живут соответствия, которые знают о конкретных возможностях игры
 * (валюты, зоны, действия в бою, узлы дерева). Сама библиотека рисунков
 * об этих типах ничего не знает и остаётся чистым доменом.
 */
object IconBindings {

    val currencies: Map<PoeCurrency, String> = PoeCurrency.entries.associateWith {
        IconResolver.forCurrencyName(it.displayName) ?: "stat-gold"
    }

    val poeRarities: Map<PoeRarity, String> = mapOf(
        PoeRarity.NORMAL to "rarity-normal", PoeRarity.MAGIC to "rarity-magic",
        PoeRarity.RARE to "rarity-rare", PoeRarity.UNIQUE to "rarity-unique"
    )

    val passiveKinds: Map<PassiveNodeKind, String> = mapOf(
        PassiveNodeKind.ORIGIN to "passive-origin", PassiveNodeKind.SMALL to "passive-small",
        PassiveNodeKind.NOTABLE to "passive-notable", PassiveNodeKind.KEYSTONE to "passive-keystone"
    )

    val battleActions: Map<BattleAction, String> = mapOf(
        BattleAction.ATTACK to "combat-attack", BattleAction.POWER to "combat-power",
        BattleAction.GUARD to "combat-guard", BattleAction.POTION to "combat-potion",
        BattleAction.FLEE to "combat-flee"
    )

    val battleStatuses: Map<BattleStatus, String> = mapOf(
        BattleStatus.ACTIVE to "combat-battle", BattleStatus.VICTORY to "combat-victory",
        BattleStatus.DEFEAT to "combat-defeat", BattleStatus.FLED to "combat-flee"
    )

    /** Стихия монстра: тем же значком, что и соответствующий урон. */
    val combatElements: Map<String, String> = mapOf(
        "physical" to "stat-physical-damage", "fire" to "stat-fire-damage", "cold" to "stat-cold-damage",
        "lightning" to "stat-lightning-damage", "chaos" to "stat-chaos-damage"
    )

    /** Иконки модификаторов встроенного каталога. Считается один раз и не зависит от базы. */
    val bundledModifiers: Map<String, String> by lazy {
        PoeCatalog.bundled.mods.entries.associate { (id, raw) -> id to forRawModifier(id, raw) }
    }

    /** Иконки базовых предметов встроенного каталога. */
    val bundledBases: Map<String, String> by lazy {
        PoeCatalog.bundled.bases.entries.associate { (id, raw) -> id to forBase(raw) }
    }

    /** Модификатор каталога: стат важнее тега, тег важнее генерации. */
    fun forRawModifier(id: String, raw: JsonObject): String {
        val statIds = raw.objects("stats").map { it.string("id") } + listOf(raw.string("text"), raw.string("name"), id)
        val source = when (raw.string("generation_type")) {
            "prefix" -> ru.descend.domain.modifiers.ModifierSource.PREFIX
            "suffix" -> ru.descend.domain.modifiers.ModifierSource.SUFFIX
            "corrupted" -> ru.descend.domain.modifiers.ModifierSource.CORRUPTION
            "unique" -> ru.descend.domain.modifiers.ModifierSource.UNIQUE
            "enchantment" -> ru.descend.domain.modifiers.ModifierSource.ENCHANTMENT
            else -> ru.descend.domain.modifiers.ModifierSource.BASE_ITEM
        }
        return IconResolver.forModifier(statIds.filter { it.isNotBlank() }, raw.strings("implicit_tags"), source)
    }

    /** Базовый предмет каталога: класс предмета, а для валют — их название. */
    fun forBase(raw: JsonObject): String =
        IconResolver.forBaseItem(raw.string("item_class"), raw.string("name"), raw.strings("tags"))

    /** Экипировка: оружие по типу, остальное по слоту. */
    fun forEquipment(equipment: Equipment): String = when (equipment) {
        is Weapon -> IconResolver.forWeapon(equipment.weaponType)
        is Armor -> IconResolver.forSlot(equipment.slot)
        is Accessory -> IconResolver.forSlot(equipment.slot)
    }

    /**
     * Иконка уже сохранённой экипировки.
     *
     * Документы, записанные до появления набора, иконки не имеют: она подставляется при чтении,
     * по базе каталога, а если база незнакома — по типу оружия или слоту. База не переписывается.
     */
    fun resolvedIcon(equipment: Equipment): String =
        equipment.icon ?: equipment.poeBaseId?.let { bundledBases[it] } ?: forEquipment(equipment)

    /** То же для каталожных предметов: `subCategory` хранит класс предмета каталога. */
    fun resolvedIcon(item: Items): String =
        item.icon ?: item.poeBaseId?.let { bundledBases[it] } ?: IconResolver.forBaseItem(item.subCategory, item.name)

    /** Узел дерева: ключевые и крупные узлы узнаваемы по форме, малые — по своей характеристике. */
    fun forPassiveNode(node: PassiveNode): String {
        if (node.kind != PassiveNodeKind.SMALL) return passiveKinds.getValue(node.kind)
        return node.effects.firstNotNullOfOrNull { IconResolver.forStat(it.stat) } ?: "passive-small"
    }

    fun forMonster(monster: Monster): String =
        if (monster.boss) "combat-boss" else combatElements[monster.element] ?: "combat-monster"

    fun forZone(@Suppress("UNUSED_PARAMETER") zone: Zone): String = "combat-zone"

    /**
     * Проставление иконок в ответах.
     *
     * Иконка считается при отдаче и не пишется в базу: набор может обновиться,
     * а сохранённые бои, деревья и каталоги остаются нетронутыми.
     */
    fun decorate(tree: PassiveTree): PassiveTree = tree.copy(nodes = tree.nodes.map { it.copy(icon = forPassiveNode(it)) })

    fun decorate(catalog: CombatCatalog): CombatCatalog = catalog.copy(zones = catalog.zones.map { zone ->
        zone.copy(icon = forZone(zone), monsters = zone.monsters.map { decorate(it) }, boss = decorate(zone.boss))
    })

    fun decorate(monster: Monster): Monster = monster.copy(icon = forMonster(monster))

    fun decorate(view: BattleView): BattleView =
        if (view.battle == null) view else view.copy(battle = view.battle.copy(monster = decorate(view.battle.monster)))

    /** Каждая иконка привязки обязана существовать в наборе: проверяется тестом и на старте. */
    fun validate() {
        val referenced = IconResolver.stats.values + IconResolver.tags.values + IconResolver.sources.values +
            IconResolver.itemClasses.values + IconResolver.currencyNames.values + IconResolver.weapons.values +
            IconResolver.slots.values + IconResolver.rarities.values + currencies.values + poeRarities.values +
            passiveKinds.values + battleActions.values + battleStatuses.values + combatElements.values
        val missing = referenced.filterNot(IconCatalog::exists).distinct()
        require(missing.isEmpty()) { "Icon bindings reference unknown icons: $missing" }
    }
}
