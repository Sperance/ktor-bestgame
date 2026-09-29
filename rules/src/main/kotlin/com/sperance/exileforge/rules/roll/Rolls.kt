package com.sperance.exileforge.rules.roll

import com.sperance.exileforge.rules.content.Catalyst
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Influence
import com.sperance.exileforge.rules.content.ItemTemplate
import com.sperance.exileforge.rules.content.ModifierDef
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.tenths
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Выпавший модификатор на копии: код описания [code], тир [tier] (0 - без тира) и доля ролла [share]
 * от дна до потолка тира, одна на все эффекты. Значения выводятся из описания - копия чисел не хранит.
 * [fractured] - закреплён Fracturing Orb; [scale] - сдвиг Ваал сверх потолка тира.
 */
@Serializable
data class Roll(
    @SerialName("c") val code: String,
    @SerialName("t") val tier: Int = 0,
    @SerialName("p") val share: Double = 0.0,
    @SerialName("f") val fractured: Boolean = false,
    @SerialName("x") val scale: Double? = null,
) {
    val rolled: Boolean get() = tier > 0

    /** Значения ролла по описанию [def]: тир по номеру, доля внутри него, сдвиг Ваал поверх. */
    fun values(def: ModifierDef): List<Double> {
        val tier = def.tier(tier) ?: return def.effects.map { 0.0 }
        val base = tier.at(share)
        return scale?.let { s -> base.map { tenths(it * s) } } ?: base
    }

    fun values(index: ContentIndex): List<Double> = index.modifier(code)?.let(::values) ?: emptyList()
}

/**
 * Копия предмета - в документе героя, на витрине, в лоте и на проводе. Хранит только своё: шаблон
 * [template], редкость, роллы, порчу, копирование, место, гнездо, влияние и качество фляги.
 * [locked] (1.28.0) - замок игрока: такую вещь не продать торговцу, не выставить на аукцион и не
 * продать самой при переполнении тайника; сферы и ремесло замок не держит.
 * [itemLevel] (1.33.0) - уровень предмета: по нему открываются тиры аффиксов; 0 - копия до ilvl, её уровень - уровень шаблона.
 * [catalyst] (1.35.0) - вид качества: с ним [quality] усиливает модификаторы вида, без него - базу; [unveil] - варианты
 * раскрытия скрытого модификатора, что ждут выбора игрока.
 */
@Serializable
data class ItemInstance(
    val id: String,
    @SerialName("t") val template: String,
    @SerialName("r") var rarity: Rarity = Rarity.COMMON,
    @SerialName("m") var rolls: List<Roll> = emptyList(),
    @SerialName("cor") var corrupted: Boolean = false,
    @SerialName("mir") var mirrored: Boolean = false,
    @SerialName("s") var slot: Slot? = null,
    @SerialName("sk") var socket: String? = null,
    @SerialName("i") var influence: Influence? = null,
    @SerialName("q") var quality: Int = 0,
    val locked: Boolean = false,
    @SerialName("il") var itemLevel: Int = 0,
    @SerialName("ct") var catalyst: Catalyst? = null,
    @SerialName("uv") var unveil: List<Roll> = emptyList(),
) {
    /** Уровень, на котором катаются аффиксы копии. */
    fun level(template: ItemTemplate): Int = if (itemLevel > 0) itemLevel else template.level

    val equipped: Boolean get() = slot != null
    val socketed: Boolean get() = !socket.isNullOrBlank()
}
