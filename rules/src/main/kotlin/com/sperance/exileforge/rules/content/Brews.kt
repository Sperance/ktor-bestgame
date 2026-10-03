package com.sperance.exileforge.rules.content

import kotlinx.serialization.Serializable

/**
 * Изделия ремёсел на заход (1.74.0). Зелье алхимика - одно на заход: его строки ложатся в контекст захода - опыт, золото
 * и редкость по ключам [AtlasStat], бой - ключами [BrewStat], их клиент кладёт на лист героя. Скарабей картографа - до
 * [scarabsPerMap] на карту, тратится с ней: его строки ложатся в строки карты ([MapStat], [CoreStat.MAP_CHESTS]).
 * Закалка кузнеца - руда поднимает качество и уровень вещи один раз ([TemperRule]).
 */
@Serializable
data class BrewRules(
    val potions: Map<String, Map<String, Double>> = emptyMap(),
    val scarabs: Map<String, Map<String, Double>> = emptyMap(),
    val scarabsPerMap: Int = 2,
    val temper: TemperRule = TemperRule(),
) {
    fun potion(code: String): Map<String, Double>? = potions[code]
    fun scarab(code: String): Map<String, Double>? = scarabs[code]
}

/**
 * Закалка: [ore] руды по уровню вещи ([ores] - нижний уровень шаблона к коду руды) поднимает качество до
 * [minQuality]..[maxQuality] и уровень предмета на 1..[maxLevels]; нужен кузнец не ниже [smithLevel].
 */
@Serializable
data class TemperRule(
    val ore: Long = 10,
    val ores: Map<String, Int> = emptyMap(),
    val minQuality: Int = 21,
    val maxQuality: Int = 30,
    val maxLevels: Int = 5,
    val smithLevel: Int = 5,
) {
    /** Руда для вещи шаблона уровня [level]: самая высокая ступень, чей порог он прошёл. */
    fun oreFor(level: Int): String? = ores.entries.filter { it.value <= level }.maxByOrNull { it.value }?.key
}

/** Строки зелий для боя: клиент кладёт их на лист героя в заходе. */
object BrewStat {
    const val RESIST = "BREW_RESIST"
    const val DAMAGE = "BREW_DAMAGE"
    const val LIFE = "BREW_LIFE"
    val ALL = listOf(RESIST, DAMAGE, LIFE)
}
