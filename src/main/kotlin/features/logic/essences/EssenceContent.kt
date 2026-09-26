package features.logic.essences

import application.enums.EnumEquipmentType
import base.exception.model.CampaignExceptions
import config.ContentResource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Ступень эссенции (с 0.69.0): с какого уровня зоны падает и перебрасывает ли редкую вещь. */
@Serializable
data class EssenceTier(val code: String, val level: Int, val rerollsRare: Boolean = false)

/**
 * Вид эссенции: гарантированная строка для оружия, брони со щитом и бижутерии с поясом - коды
 * модификаторов - и модификатор стража кристалла с этой эссенцией.
 */
@Serializable
data class EssenceKind(val code: String, val weapon: String, val armour: String, val jewellery: String, val monster: String)

/** Кристаллы зоны: окно как у сундуков, внутри эссенции ступени зоны, страж - редкий монстр зоны. */
@Serializable
data class CrystalRule(
    val count: List<Int>,
    val refreshHours: Double,
    val essences: List<Int>,
    /** Шанс, что эссенция на ступень ниже зоны. */
    val lowerChance: Double,
    val modifierPools: List<String>,
    /** Шанс книги умения со стража. */
    val bookChance: Double,
    /** Сфера Ваал на кристалле: веса исходов - все эссенции выше, одна особая, страж сильнее. */
    val vaal: Map<String, Int>,
    /** Насколько сильнее страж - процент здоровья и урона. */
    val stronger: Double,
)

/** Сгущение в Алхимии: [inputs] одинаковых - одна ступенью выше; [levels] - уровень ремесла для каждой ступени. */
@Serializable
data class CondenseRule(val inputs: Int, val levels: List<Int>, val seconds: Int)

/** Файл `essences.json` (0.69.0). */
@Serializable
data class EssenceBook(
    val tiers: List<EssenceTier>,
    val kinds: List<EssenceKind>,
    val specials: List<EssenceKind>,
    val crystals: CrystalRule,
    val condense: CondenseRule,
)

/** Эссенция в сумке: вид, ступень (у особой - 0) и её код предмета. */
data class Essence(val kind: EssenceKind, val tier: Int, val special: Boolean) {
    val code: String get() = EssenceContent.code(kind.code, tier, special)
}

/**
 * Эссенции - правила мира: виды, ступени, кристаллы и сгущение. Предметы эссенций лежат в
 * `items.json` под категорией [CATEGORY]: `ESSENCE_<вид>_<ступень>` и `ESSENCE_<особый вид>`.
 */
object EssenceContent {
    const val FILE = "essences.json"
    const val CATEGORY = "ESSENCE"
    const val PREFIX = "ESSENCE_"

    private val json = Json { ignoreUnknownKeys = true }

    val book: EssenceBook by lazy { load(ContentResource.read(FILE)) }

    /** Все эссенции по коду предмета. */
    val essences: Map<String, Essence> by lazy {
        (book.kinds.flatMap { kind -> (1..book.tiers.size).map { Essence(kind, it, false) } } + book.specials.map { Essence(it, 0, true) })
            .associateBy { it.code }
    }

    fun code(kind: String, tier: Int, special: Boolean): String = if (special) "$PREFIX$kind" else "$PREFIX${kind}_$tier"

    /** Ступень зоны [level]: последняя, чей уровень не выше. */
    fun tierOf(level: Int): Int = book.tiers.indexOfLast { it.level <= level }.coerceAtLeast(0) + 1

    fun load(text: String): EssenceBook = json.decodeFromString(EssenceBook.serializer(), text).also(::validate)

    private fun validate(book: EssenceBook) {
        fun fail(what: String): Nothing = throw CampaignExceptions.funExceptionContent("essences", what)
        if (book.tiers.isEmpty() || book.tiers.zipWithNext().any { (a, b) -> a.level >= b.level }) fail("tiers")
        val codes = (book.kinds + book.specials).map { it.code }
        if (codes.toSet().size != codes.size) fail("kinds")
        val rule = book.crystals
        if (rule.count.size != 2 || rule.count[0] > rule.count[1] || rule.essences.size != 2 || rule.essences[0] < 1 || rule.essences[0] > rule.essences[1]) fail("crystals")
        if (rule.vaal.keys != setOf(VAAL_UPGRADE, VAAL_SPECIAL, VAAL_STRONGER) || rule.modifierPools.isEmpty()) fail("crystal vaal")
        if (book.condense.levels.size != book.tiers.size - 1 || book.condense.inputs < 2) fail("condense")
    }

    const val VAAL_UPGRADE = "UPGRADE"
    const val VAAL_SPECIAL = "SPECIAL"
    const val VAAL_STRONGER = "STRONGER"
}

/** Правила эссенций над предметами: чью гарантию берёт вещь слота. */
object EssenceRules {
    /** Семейство вещи для гарантии: оружие с колчаном, броня со щитом и крыльями, бижутерия с поясом. */
    enum class Family { WEAPON, ARMOUR, JEWELLERY }

    fun family(slot: EnumEquipmentType): Family? = when (slot) {
        EnumEquipmentType.WEAPON_1H, EnumEquipmentType.WEAPON_2H, EnumEquipmentType.QUIVER -> Family.WEAPON
        EnumEquipmentType.HELMET, EnumEquipmentType.BODY, EnumEquipmentType.GLOVES,
        EnumEquipmentType.BOOTS, EnumEquipmentType.SHIELD, EnumEquipmentType.WINGS -> Family.ARMOUR
        EnumEquipmentType.RING, EnumEquipmentType.RING_2, EnumEquipmentType.AMULET,
        EnumEquipmentType.BELT -> Family.JEWELLERY
        else -> null
    }

    /** Код гарантированного модификатора эссенции на вещи слота [slot]; null - вещь эссенцию не берёт. */
    fun guarantee(essence: Essence, slot: EnumEquipmentType): String? = when (family(slot)) {
        Family.WEAPON -> essence.kind.weapon
        Family.ARMOUR -> essence.kind.armour
        Family.JEWELLERY -> essence.kind.jewellery
        null -> null
    }
}
