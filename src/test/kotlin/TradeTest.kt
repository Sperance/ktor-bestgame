import application.enums.EnumModifierOperation
import application.enums.EnumRarity
import application.enums.EnumStatStock
import features.logic.modifiers.ModifierCalculator
import features.logic.modifiers.StatOperation
import features.logic.trade.SellPrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правила, которые считает сервер и печатает клиент: цена выкупа и вклад
 * набора модификаторов. Оба чистые - Mongo здесь не нужна.
 */
class TradeTest {

    // ==================== Цена выкупа ====================

    /**
     * Цена растёт от редкости и от числа аффиксов, а не от их значений.
     *
     * Оценивать каждый ролл отдельно значило бы считать баланс в двух местах;
     * счёт аффиксов - то же самое различение "хороший предмет дороже", но одним
     * числом, которое уже лежит на экземпляре.
     */
    @Test
    fun a_rarer_item_with_more_affixes_is_worth_more() {
        val plain = SellPrice.of(template(price = 100, rarity = EnumRarity.COMMON), emptyList(), emptyMap())
        val rare = SellPrice.of(template(price = 100, rarity = EnumRarity.RARE), emptyList(), emptyMap())
        assertTrue("редкий не дороже обычного: $rare против $plain", rare > plain)

        val rolled = SellPrice.of(template(price = 100, rarity = EnumRarity.RARE), rolls(4), emptyMap())
        assertTrue("аффиксы не подняли цену: $rolled против $rare", rolled > rare)

        // Уникальный дороже мифического, потому что аффиксов не роллит вовсе.
        assertTrue(SellPrice.factor(EnumRarity.UNIQUE) > SellPrice.factor(EnumRarity.MYTHICAL))
    }

    /**
     * STOCK_GOLD - задел: пока его никто не выдаёт, он равен нулю и цену не двигает.
     */
    @Test
    fun the_gold_stat_raises_the_price_and_costs_nothing_while_it_is_zero() {
        val base = SellPrice.of(template(price = 200, rarity = EnumRarity.COMMON), emptyList(), emptyMap())
        val zero = SellPrice.of(
            template(price = 200, rarity = EnumRarity.COMMON), emptyList(),
            mapOf(EnumStatStock.STOCK_GOLD to 0.0)
        )
        assertEquals(base, zero)

        val richer = SellPrice.of(
            template(price = 200, rarity = EnumRarity.COMMON), emptyList(),
            mapOf(EnumStatStock.STOCK_GOLD to 50.0)
        )
        assertEquals(base * 3 / 2, richer)
    }

    /** Торговец никогда не платит ноль: предмет всегда чего-то да стоит. */
    @Test
    fun nothing_is_ever_bought_for_nothing() {
        assertTrue(SellPrice.of(template(price = 0, rarity = EnumRarity.COMMON), emptyList(), emptyMap()) >= 1L)
    }

    // ==================== Вклад набора модификаторов ====================

    /**
     * Вклад считается без базы, поэтому проценты остаются процентами.
     *
     * Это и есть причина, по которой дерево не умело показать "увеличение
     * физического урона": итог от пустой базы давал 0 * 1.4 = 0, и процентные
     * узлы пропадали из ответа целиком.
     */
    @Test
    fun a_percentage_survives_having_no_base_to_stand_on() {
        val contributions = ModifierCalculator.contributions(
            listOf(
                operation(EnumStatStock.STOCK_ATTACK_PHYSICAL, EnumModifierOperation.INCREASED, 25.0),
                operation(EnumStatStock.STOCK_ATTACK_PHYSICAL, EnumModifierOperation.INCREASED, 15.0),
            )
        )
        assertEquals(1, contributions.size)
        assertEquals(40.0, contributions.first().value, 0.001)
        assertEquals(EnumModifierOperation.INCREASED, contributions.first().operation)
    }

    /** Два MORE перемножаются: 20% и 30% дают 56%, а не 50%. */
    @Test
    fun two_more_modifiers_multiply_rather_than_add() {
        val contributions = ModifierCalculator.contributions(
            listOf(
                operation(EnumStatStock.STOCK_HEALTH, EnumModifierOperation.MORE, 20.0),
                operation(EnumStatStock.STOCK_HEALTH, EnumModifierOperation.MORE, 30.0),
            )
        )
        assertEquals(56.0, contributions.single().value, 0.001)
    }

    /** ADD и INCREASED по одному стату - это две разные строки, а не одно число. */
    @Test
    fun an_addition_and_a_percentage_are_two_different_lines() {
        val contributions = ModifierCalculator.contributions(
            listOf(
                operation(EnumStatStock.STOCK_HEALTH, EnumModifierOperation.ADD, 60.0),
                operation(EnumStatStock.STOCK_HEALTH, EnumModifierOperation.INCREASED, 10.0),
            )
        )
        assertEquals(2, contributions.size)
        assertEquals(
            setOf(EnumModifierOperation.ADD, EnumModifierOperation.INCREASED),
            contributions.map { it.operation }.toSet()
        )
        // SET не складывается ни с чем: побеждает последний.
        val set = ModifierCalculator.contributions(
            listOf(
                operation(EnumStatStock.STOCK_HEALTH, EnumModifierOperation.SET, 500.0),
                operation(EnumStatStock.STOCK_HEALTH, EnumModifierOperation.SET, 900.0),
            )
        )
        assertEquals(900.0, set.single().value, 0.001)
    }

    /** Ноль не строка: узел, который ничего не даёт, в списке не появляется. */
    @Test
    fun a_contribution_of_zero_is_left_out() {
        assertTrue(
            ModifierCalculator.contributions(
                listOf(operation(EnumStatStock.STOCK_HEALTH, EnumModifierOperation.ADD, 0.0))
            ).isEmpty()
        )
    }

    private fun operation(stat: EnumStatStock, operation: EnumModifierOperation, value: Double) =
        StatOperation(stat, operation, value)

    private fun rolls(count: Int) = List(count) { features.logic.modifiers.Modifier("mod-$it", mutableListOf(1.0)) }

    private fun template(price: Long, rarity: EnumRarity) =
        features.data.equipment.equipment_data.Armor(
            code = "TEST_ARMOUR",
            slot = application.enums.EnumEquipmentType.BODY,
            rarity = rarity,
        ).also { it.price = price }
}
