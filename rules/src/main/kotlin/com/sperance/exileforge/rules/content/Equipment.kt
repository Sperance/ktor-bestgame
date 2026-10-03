package com.sperance.exileforge.rules.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Слот экипировки. `RING_2` и `FLASK_2`/`FLASK_3` - места надетого, шаблона с таким слотом не бывает. */
@Serializable
enum class Slot {
    HELMET,
    BODY,
    GLOVES,
    RING,
    BOOTS,
    WINGS,
    BELT,
    WEAPON_1H,
    WEAPON_2H,
    QUIVER,
    SHIELD,
    AMULET,
    RING_2,
    JEWEL,
    MAP,
    FLASK,
    FLASK_2,
    FLASK_3,
    TOOL_MINING,
    TOOL_HERBALISM,
    TOOL_WOODCUTTING,
    TOOL_SMITHING,
    TOOL_ALCHEMY,
    TOOL_CARTOGRAPHY,
    TOOL_ENCHANTING,

    /** Ошейник питомца (1.35.0): место героя, чьи строки `STOCK_PET_*` доходят до питомца; только ремесло кузнеца. */
    COLLAR,

    ;

    val isTool: Boolean get() = name.startsWith("TOOL_")
    val isFlask: Boolean get() = this == FLASK || this == FLASK_2 || this == FLASK_3
    val isWeapon: Boolean get() = this == WEAPON_1H || this == WEAPON_2H

    /** Самоцвет и карта: не носятся на теле, не бывают обычными и пустыми. */
    val isJewelLike: Boolean get() = this == JEWEL || this == MAP

    /** Может ли нести влияние: не самоцвет, не карта, не инструмент и не фляга. */
    val influenceable: Boolean get() = !isJewelLike && !isTool && !isFlask && this != COLLAR

    /** Броня под обрезки брони (1.35.0). */
    val isArmour: Boolean get() = this == HELMET || this == BODY || this == GLOVES || this == BOOTS || this == SHIELD || this == WINGS

    /** Тег слота в таблицах: оба оружия - `weapon`, инструменты - `tool`, места фляг - `flask`, второе кольцо - `ring`. */
    val tag: String get() = when {
        isWeapon -> "weapon"
        this == RING_2 -> "ring"
        isFlask -> "flask"
        isTool -> "tool"
        else -> name.lowercase()
    }

    companion object {
        val FLASKS = listOf(FLASK, FLASK_2, FLASK_3)
        val RINGS = listOf(RING, RING_2)
        fun of(name: String): Slot? = entries.firstOrNull { it.name == name }
    }
}

/** Вид оружия. Оружие заклинаний - жезл, посох (двуручный, блокирует) и скипетр ([spell]). */
@Serializable
enum class WeaponType {
    SWORD,
    LONGSWORD,
    BOW,
    WAND,
    AXE,
    DOUBLEAXE,
    DOUBLESWORD,
    BLADE,
    STAFF,
    SCEPTRE,
    ;

    /** Оружие заклинателя: его таблицы катят строки заклинаний, а носитель бьёт из второго ряда, как с жезлом. */
    val spell: Boolean get() = this == WAND || this == STAFF || this == SCEPTRE
}

/**
 * Куда встаёт надеваемая вещь и что она снимает - руки и кольца PoE: двуручное занимает обе руки,
 * лук стреляет только из колчана, прочее одноручное носят со щитом; колец два, мест фляг три.
 */
object EquipSlots {
    /** Место вещи слота [slot]: кольцо и фляга - на названное [requested] или первое свободное, иначе на первое. */
    fun target(slot: Slot, requested: Slot?, occupied: Collection<Slot>): Slot {
        val places = when (slot) {
            Slot.RING -> Slot.RINGS
            Slot.FLASK -> Slot.FLASKS
            else -> return slot
        }
        if (requested in places) return requested!!
        return places.firstOrNull { it !in occupied } ?: places.first()
    }

    /** Слоты, которые освобождает вещь в [slot], кроме её собственного; [weapon] - вид надеваемого оружия, [wornWeapon] - надетого одноручного. */
    fun displaced(slot: Slot, weapon: WeaponType?, wornWeapon: WeaponType?): Set<Slot> {
        val wornBow = wornWeapon == WeaponType.BOW
        return when (slot) {
            Slot.WEAPON_2H -> setOf(Slot.WEAPON_1H, Slot.SHIELD, Slot.QUIVER)
            Slot.WEAPON_1H -> if (weapon == WeaponType.BOW) setOf(Slot.WEAPON_2H, Slot.SHIELD) else setOf(Slot.WEAPON_2H, Slot.QUIVER)
            Slot.SHIELD -> if (wornBow) setOf(Slot.WEAPON_2H, Slot.QUIVER, Slot.WEAPON_1H) else setOf(Slot.WEAPON_2H, Slot.QUIVER)
            Slot.QUIVER -> if (wornWeapon != null && !wornBow) setOf(Slot.WEAPON_2H, Slot.SHIELD, Slot.WEAPON_1H) else setOf(Slot.WEAPON_2H, Slot.SHIELD)
            else -> emptySet()
        }
    }
}

/** Редкость копии: сколько аффиксов она несёт - решают правила ([RarityLimits]); уникалка и мифик закреплены шаблоном. */
@Serializable
enum class Rarity {
    COMMON,
    MAGIC,
    RARE,
    UNIQUE,
    MYTHICAL,
    ;

    val fixed: Boolean get() = this == UNIQUE || this == MYTHICAL

    companion object {
        fun of(name: String): Rarity? = entries.firstOrNull { it.name == name }
    }
}

/** Влияние копии: открывает ей таблицу `influence:<влияние>[:<слот>]`; ставит сфера или добыча Бездны, снять нельзя. */
@Serializable
enum class Influence { SHAPER, ELDER, ABYSS }

@Serializable
enum class TemplateKind { WEAPON, ARMOR, ACCESSORY }

/** Строка уникалки в файле: эффект и диапазон её единственного тира. */
@Serializable
data class UniqueLine(
    val stat: String,
    val op: Op = Op.ADD,
    val range: Range,
    /** Условие боя (1.36.0): строка уникалки, как условный модификатор, работает только пока оно держится. */
    @SerialName("when") val condition: Condition? = null,
)

/**
 * Шаблон предмета (`equipment.json`). Копия не хранит ни базы, ни закреплённых строк: [base] -
 * броня, урон, скорость - готовыми строками, [fixed] - коды имплиситов, [lines] - строки уникалки,
 * из которых собираются описания `UNIQUE_<код>_<i>`; [tables] - теги таблиц, откуда катятся аффиксы.
 */

/** Шаблон карты (1.43.0): один на все зоны, зона - в [com.sperance.exileforge.rules.roll.ItemInstance.mapZone]. */
const val MAP_TEMPLATE = "MAP"

@Serializable
data class ItemTemplate(
    val code: String,
    val slot: Slot,
    val kind: TemplateKind = TemplateKind.ACCESSORY,
    val rarity: Rarity = Rarity.COMMON,
    val level: Int = 1,
    val requiredLevel: Int = 1,
    val requiredStrength: Int = 0,
    val requiredDexterity: Int = 0,
    val requiredIntelligence: Int = 0,
    val weaponType: WeaponType? = null,
    val price: Long? = null,
    val base: List<Line> = emptyList(),
    val fixed: List<String> = emptyList(),
    val tables: List<String> = emptyList(),
    val lines: List<List<UniqueLine>> = emptyList(),
    /** Копия всегда осквернена (1.32.0): сферы её не меняют - уникалки Алтаря, зеркальное кольцо. */
    val corrupted: Boolean = false,
    /** Классовая уникалка (1.71.0): падает кому угодно, надеть может только герой этого класса. */
    val heroClass: String? = null,
) {
    val unique: Boolean get() = rarity.fixed
    val demanding: Boolean get() = requiredLevel > 1 || requiredStrength > 0 || requiredDexterity > 0 || requiredIntelligence > 0

    /** Сколько у уникалки своих эффектов (1.19.0): строк силы `POWER_*` и особых строк фляги `FLASK_*`. */
    val uniqueEffects: Int get() = lines.count { line -> line.any { it.stat.startsWith(POWER_PREFIX) || it.stat.startsWith(FLASK_PREFIX) } }

    /** Код описания строки уникалки [index]. */
    fun lineCode(index: Int): String = "UNIQUE_${code}_$index"

    /** Все закреплённые описания копии: имплиситы шаблона и строки уникалки. */
    val fixedCodes: List<String> get() = fixed + lines.indices.map(::lineCode)

    /** Семейства строк уникалки: по одному тиру на уровне предмета, как в файле. */
    fun uniqueFamilies(): List<ModifierFamily> = lines.mapIndexed { index, line ->
        ModifierFamily(
            code = lineCode(index),
            source = Source.UNIQUE,
            effects = line.map { Effect(it.stat, it.op, condition = it.condition) },
            tiers = listOf(Tier(level, 0, line.map { it.range })),
            tags = listOf("unique", slot.tag),
        )
    }
}

private const val POWER_PREFIX = "POWER_"
private const val FLASK_PREFIX = "FLASK_"

@Serializable
data class EquipmentFile(val templates: List<ItemTemplate> = emptyList())

/** Стакающийся предмет сумки (`items.json`): сферы, материалы, книги, эссенции. */
@Serializable
data class Item(val code: String, val category: String, val subCategory: String = "", val price: Long = 0) {
    companion object {
        const val CURRENCY = "CURRENCY"
        const val BOOK = "BOOK"
        const val ESSENCE = "ESSENCE"
        const val MATERIAL = "MATERIAL"

        /** Яйца и сферы питомцев (1.5.0). */
        const val PET = "PET"

        /** Знамения (1.35.0): тратятся вместе со сферой и меняют её действие. */
        const val OMEN = "OMEN"

        /** Сундуки-добыча (1.71.0): открываются у героя, не торгуются. */
        const val CHEST = "CHEST"
    }
}

@Serializable
data class ItemsFile(val items: List<Item> = emptyList())

/** Места аффиксов редкости: сколько префиксов и суффиксов помещается и сколько аффиксов выпадает. */
@Serializable
data class RarityLimits(val prefixes: Int = 0, val suffixes: Int = 0, val affixes: List<Int> = listOf(0, 0)) {
    val floor: Int get() = affixes[0]
    val ceiling: Int get() = affixes.getOrElse(1) { affixes[0] }
}
