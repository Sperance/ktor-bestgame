package ru.descend.domain.icons

import kotlinx.serialization.Serializable

/**
 * Группы иконок.
 *
 * Категория не влияет на отрисовку, она нужна клиенту
 * для вкладок, фильтров и предзагрузки нужного набора.
 */
@Serializable
@kotlinx.serialization.SerialName("domain.icons.IconCategory")
enum class IconCategory { WEAPON, ARMOUR, JEWELLERY, CURRENCY, STAT, AFFIX, RARITY, PASSIVE, COMBAT, ITEM, UI }

/**
 * Одна иконка набора.
 *
 * [body] — фрагмент SVG без рамки и градиентов: он рисуется внутри группы,
 * которая уже задаёт `stroke="url(#edge)"`, толщину и скругления.
 * Заливка берётся из `url(#core)` там, где нужен объём.
 *
 * Цвета хранятся вместе с рисунком: клиент может либо взять готовый SVG,
 * либо нарисовать свой примитив и покрасить его теми же [tint] / [deep].
 */
@Serializable
@kotlinx.serialization.SerialName("domain.icons.IconArt")
data class IconArt(
    val id: String,
    val title: String,
    val category: IconCategory,
    val tint: String,
    val deep: String,
    val body: String,
    val keywords: List<String> = emptyList()
) {
    init {
        require(id.matches(Regex("[a-z0-9-]{3,48}"))) { "Icon id must be kebab-case: $id" }
        require(title.isNotBlank() && body.isNotBlank())
        require(tint.matches(HEX) && deep.matches(HEX)) { "Icon $id has a non-hex palette" }
    }
    companion object { private val HEX = Regex("#[0-9a-fA-F]{6}") }
}

/** Палитра набора. Один набор оттенков на все иконки — поэтому они выглядят как одна серия. */
object IconPalette {
    const val STEEL_T = "#dbe4f0"; const val STEEL_D = "#5d6f8a"
    const val ASH_T = "#c2ccd8"; const val ASH_D = "#56606e"
    const val BRONZE_T = "#e3bb82"; const val BRONZE_D = "#8a6a3a"
    const val GOLD_T = "#f5da8c"; const val GOLD_D = "#a8791f"
    const val RED_T = "#f0736a"; const val RED_D = "#8e2b25"
    const val CRIMSON_T = "#ef5f8b"; const val CRIMSON_D = "#8d1f45"
    const val BLUE_T = "#7fb4f5"; const val BLUE_D = "#2e5a99"
    const val CYAN_T = "#8fe4f0"; const val CYAN_D = "#22707f"
    const val TEAL_T = "#79d3c4"; const val TEAL_D = "#237a6c"
    const val GREEN_T = "#8fe0a0"; const val GREEN_D = "#2f7a45"
    const val ORANGE_T = "#f7a35c"; const val ORANGE_D = "#a4521a"
    const val YELLOW_T = "#f7e06a"; const val YELLOW_D = "#9a8410"
    const val VIOLET_T = "#c3a8ff"; const val VIOLET_D = "#5b3ea8"
    const val PURPLE_T = "#d08ce8"; const val PURPLE_D = "#6c2f86"
    const val SILVER_T = "#eef2f7"; const val SILVER_D = "#7d8896"
}
