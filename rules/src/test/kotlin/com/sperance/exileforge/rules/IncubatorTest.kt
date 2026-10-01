package com.sperance.exileforge.rules

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ContentLoader
import com.sperance.exileforge.rules.content.IncubatorRules
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.Menagerie
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Инкубатор 1.67.0: срок от 5 минут до 8 часов, яйцо зреет по часам, мест не больше трёх, сокращение срока - не больше 75%. */
class IncubatorTest {
    private val index: ContentIndex by lazy {
        val resources = File(System.getProperty("content.resources") ?: "../src/main/resources")
        ContentLoader.load { File(resources, "content/$it").readText() }
    }
    private val rules get() = index.pets.incubator
    private val minute = 60_000L

    @Test
    fun incubationFinishesAfterItsDuration() {
        val pets = Menagerie(index)
        assertEquals(5 * minute, rules.durationMillis(1, pets.maxLevel, Rarity.COMMON, 0.0))
        assertEquals(480 * minute, rules.durationMillis(pets.maxLevel, pets.maxLevel, Rarity.RARE, 0.0))
        assertEquals(480 * minute / 4, rules.durationMillis(pets.maxLevel, pets.maxLevel, Rarity.RARE, -500.0))

        val egg = index.pets.eggs.values.first()
        val start = 1_000_000L
        val incubation = pets.incubate(egg, 0, 40, mapOf(IncubatorRules.HATCH_TIME to -20.0), start, Dice(5L))!!
        assertEquals(rules.durationMillis(incubation.level, pets.maxLevel, incubation.rarity, -20.0), incubation.readyAt - start)
        assertFalse(incubation.ready(incubation.readyAt - 1))
        assertTrue(incubation.ready(incubation.readyAt))
        val pet = pets.hatch(incubation, "p", Dice(6L))!!
        assertEquals(incubation.rarity, pet.rarity)
        assertEquals(incubation.level, pet.level)
        assertTrue(pet.level in 20..40)
    }

    @Test
    fun incubatorSlotsAreCappedAtThree() {
        val pets = Menagerie(index)
        assertEquals(1, pets.incubatorSlots(emptyMap()))
        assertEquals(3, pets.incubatorSlots(mapOf(IncubatorRules.SLOTS to 2.0)))
        assertEquals(3, pets.incubatorSlots(mapOf(IncubatorRules.SLOTS to 5.0)))
    }
}
