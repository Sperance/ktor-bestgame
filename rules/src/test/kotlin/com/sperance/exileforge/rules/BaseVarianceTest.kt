package com.sperance.exileforge.rules

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ContentLoader
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.sheet.SheetCalculator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Разброс базы 1.67.0: качество базы 90..110 и +1% за уровень предмета сверх шаблона, не больше +20%; блок не меняется. */
class BaseVarianceTest {
    private val index: ContentIndex by lazy {
        val resources = File(System.getProperty("content.resources") ?: "../src/main/resources")
        ContentLoader.load { File(resources, "content/$it").readText() }
    }

    @Test
    fun baseQualityAndItemLevelScaleDefencesButNotBlock() {
        val template = index.template("SPLINTERED_TOWER_SHIELD")!!
        val calc = SheetCalculator(index)
        fun base(item: ItemInstance) = calc.itemBase(template, item)
        val plain = base(ItemInstance("a", template.code))
        val armour = plain.getValue("STOCK_ARMOR")
        assertEquals(armour * 1.1, base(ItemInstance("b", template.code, baseQuality = 110)).getValue("STOCK_ARMOR"), 1.0)
        assertEquals(armour * 1.05, base(ItemInstance("c", template.code, itemLevel = template.level + 5)).getValue("STOCK_ARMOR"), 1.0)
        val capped = base(ItemInstance("d", template.code, baseQuality = 90, itemLevel = template.level + 80))
        assertEquals(armour * 0.9 * 1.2, capped.getValue("STOCK_ARMOR"), 1.0)
        plain.filterKeys { it != "STOCK_ARMOR" }.forEach { (stat, value) -> assertEquals(value, capped.getValue(stat), stat) }

        val dice = Dice(7L)
        val rolled = List(50) { ItemFactory(index).create("i$it", template, Rarity.COMMON, dice).baseQuality }
        assertTrue(rolled.all { it in 90..110 } && rolled.distinct().size > 1)
    }
}
