package features.logic.trade

import application.enums.EnumRarity
import application.enums.EnumStatStock
import application.enums.IntEnumStat
import features.data.equipment.equipment_data.Equipment
import features.logic.modifiers.Modifier
import kotlin.math.floor

/**
 * Во сколько золота торговец оценивает предмет.
 *
 * Цену назначает сервер; с 0.41.0 клиент считает её по этому же правилу, чтобы показать
 * заранее, - поэтому его изменение меняет и клиентский `SellPrice`. Правило собрано из
 * трёх вещей, каждая из которых уже есть у предмета, - базовой цены шаблона,
 * редкости экземпляра и того, сколько на нём выролено аффиксов. Роллы входят
 * счётом, а не значениями: хорошие роллы стоят дороже плохих, но оценивать
 * каждый модификатор отдельно значило бы считать баланс в двух местах.
 *
 * [EnumStatStock.STOCK_GOLD] - задел: характеристика уже есть в перечислении,
 * её пока никто не выдаёт, и пока она равна нулю множитель равен единице.
 * Как только появится модификатор "увеличение стоимости продажи", он начнёт
 * работать здесь сам, без правок этого правила.
 */
object SellPrice {

    /** Сколько аффиксов прибавляют к цене - доля от базы за каждый. */
    private const val AFFIX_SHARE = 0.15

    /**
     * @param rarity редкость экземпляра: до 0.41.0 бралась редкость шаблона, и редкая вещь
     * продавалась как обычная
     */
    fun of(template: Equipment, rarity: EnumRarity, rolled: Collection<Modifier>, stats: Map<IntEnumStat, Double>): Long {
        val rarityFactor = factor(rarity)
        val affixFactor = 1.0 + AFFIX_SHARE * rolled.size
        val goldFactor = 1.0 + (stats[EnumStatStock.STOCK_GOLD] ?: 0.0) / 100.0

        val price = template.price * rarityFactor * affixFactor * goldFactor
        // Торговец никогда не платит ноль: предмет всегда чего-то да стоит.
        return floor(price).toLong().coerceAtLeast(1L)
    }

    /**
     * Множитель редкости.
     *
     * Растёт вместе с вместимостью аффиксов, которую редкость и задаёт, а
     * уникальный стоит дороже мифического, потому что не роллится вовсе.
     */
    fun factor(rarity: EnumRarity): Double = when (rarity) {
        EnumRarity.COMMON -> 1.0
        EnumRarity.UNCOMMON -> 1.5
        EnumRarity.RARE -> 2.5
        EnumRarity.EPIC -> 4.0
        EnumRarity.MYTHICAL -> 6.0
        EnumRarity.UNIQUE -> 8.0
    }
}

/**
 * Чем кончилась продажа торговцу.
 *
 * Экземпляра больше нет, поэтому наружу уходит только то, что игроку нужно
 * увидеть: код проданного, сколько за него дали и сколько золота стало.
 */
@kotlinx.serialization.Serializable
data class SellOutcome(
    val inventoryId: String,
    val code: String,
    val gold: Long,
    val money: Long,
)
