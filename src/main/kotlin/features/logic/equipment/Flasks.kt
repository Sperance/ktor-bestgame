package features.logic.equipment

import application.enums.EnumCurrencyOrb
import application.enums.EnumRarity
import features.data.equipment.equipment_data.Equipment
import features.data.inventory.CharacterEquipment

/** Вид фляги (с 0.69.0): что она делает глотком. */
enum class FlaskKind { LIFE, MANA, UTILITY }

/**
 * Фляги - правило сервера (с 0.69.0).
 *
 * Фляга носится на одном из трёх мест пояса и в лист героя не входит: её база и строки действуют,
 * только пока она выпита в бою, а бой считает клиент. Сервер держит то, что фляга есть: обычная,
 * волшебная (префикс и суффикс) или уникальная - редкой не бывает; какие сферы её берут; качество.
 */
object FlaskRules {

    /** Фляга нового героя - на первом месте пояса. */
    const val STARTER = "FLASK_SMALL_LIFE"

    /** Потолок качества фляги, в процентах: каждый процент - плюс процент эффекта или восстановления. */
    const val MAX_QUALITY = 20

    /** Сколько качества даёт «Стеклодув»: обычной фляге и волшебной. */
    const val BAUBLE_COMMON = 2
    const val BAUBLE_MAGIC = 1

    /** Сферы, которые фляга принимает; прочие отвергает - как в POE. */
    val ORBS: Set<EnumCurrencyOrb> = setOf(
        EnumCurrencyOrb.ORB_OF_TRANSMUTATION, EnumCurrencyOrb.ORB_OF_ALTERATION, EnumCurrencyOrb.ORB_OF_AUGMENTATION,
        EnumCurrencyOrb.ORB_OF_SCOURING, EnumCurrencyOrb.ORB_OF_CHANCE, EnumCurrencyOrb.BLESSED_ORB, EnumCurrencyOrb.DIVINE_ORB,
        EnumCurrencyOrb.VAAL_ORB, EnumCurrencyOrb.GLASSBLOWERS_BAUBLE,
    )

    fun isFlask(template: Equipment): Boolean = template.slot.isFlask

    /** Вид фляги по её базе: восстановление здоровья, маны или ни того ни другого. */
    fun kind(template: Equipment): FlaskKind = when {
        template.baseParams.any { it.modifierCode == "BASE_FLASK_LIFE" } -> FlaskKind.LIFE
        template.baseParams.any { it.modifierCode == "BASE_FLASK_MANA" } -> FlaskKind.MANA
        else -> FlaskKind.UTILITY
    }

    /** Редкость, с которой фляга появляется: редкая становится волшебной, прочее - как было. */
    fun rarity(template: Equipment, wanted: EnumRarity): EnumRarity =
        if (isFlask(template) && wanted == EnumRarity.RARE) EnumRarity.UNCOMMON else wanted

    /** Сколько качества прибавит «Стеклодув» этой фляге сейчас; 0 - больше не прибавить. */
    fun baubleStep(item: CharacterEquipment): Int {
        val step = when (item.rarity) {
            EnumRarity.COMMON -> BAUBLE_COMMON
            EnumRarity.UNCOMMON -> BAUBLE_MAGIC
            else -> 0
        }
        return step.coerceAtMost(MAX_QUALITY - item.quality).coerceAtLeast(0)
    }
}
