package com.sperance.exileforge.rules

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.ContentLoader
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.roll.Streams
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.text.ModifierText
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Контент сервера читается, сходится и каждый модификатор имеет текст на каждом языке. */
class ContentLoadTest {
    private val resources = File(System.getProperty("content.resources") ?: "../src/main/resources")

    private fun index(): ContentIndex = ContentLoader.load { File(resources, "content/$it").readText() }

    @Test
    fun contentLoadsAndValidates() {
        val index = index()
        assertTrue(index.definitions.size > 1000, "definitions ${index.definitions.size}")
        assertTrue(index.bench.isNotEmpty())
        assertTrue(index.zones.size > 100)
        assertEquals(64, index.hash.length)
    }

    @Test
    fun everyModifierHasTextInEveryLanguage() {
        val index = index()
        val strings = MapSerializer(String.serializer(), String.serializer())
        val common = RulesJson.decodeFromString(strings, File(resources, "locale/common.json").readText())
        listOf("en", "ru").forEach { language ->
            val merged = common + RulesJson.decodeFromString(strings, File(resources, "locale/$language.json").readText())
            val text = ModifierText(index.stats) { merged[it] }
            val missing = index.definitions.filter { text.template(it) == null }.map { it.code }
            assertTrue(missing.isEmpty(), "$language: no text for ${missing.take(10)} (${missing.size})")
        }
    }

    @Test
    fun everyAchievementAndTitleHasTextInEveryLanguage() {
        val index = index()
        val strings = MapSerializer(String.serializer(), String.serializer())
        listOf("en", "ru").forEach { language ->
            val words = RulesJson.decodeFromString(strings, File(resources, "locale/$language.json").readText())
            val keys = index.achievements.achievements.flatMap { listOf("achievement.${it.code}.name", "achievement.${it.code}.desc") } +
                index.achievements.achievements.filter { it.title.isNotBlank() }.map { "title.${it.title}" }
            val missing = keys.filter { words[it].isNullOrBlank() }
            assertTrue(missing.isEmpty(), "$language: $missing")
        }
    }

    @Test
    fun rollsAreDeterministic() {
        val index = index()
        val template = index.templates.values.first { it.rarity == Rarity.COMMON && it.tables.isNotEmpty() && !it.slot.isJewelLike }
        val factory = ItemFactory(index)
        val a = factory.create("x", template, Rarity.RARE, Streams(42).of("loot", 3))
        val b = factory.create("x", template, Rarity.RARE, Streams(42).of("loot", 3))
        assertEquals(a, b)
        assertTrue(a.rolls.count { index.modifier(it.code)?.affix == true } in 4..6)
        val other = factory.create("x", template, Rarity.RARE, Streams(42).of("loot", 4))
        assertTrue(a != other)
        assertNotNull(Dice(1).share())
    }
}
