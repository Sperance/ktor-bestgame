package ru.descend.exileforge

import com.mongodb.MongoClientSettings
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.TakenNode
import com.sperance.exileforge.rules.roll.ChestWindow
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.Roll
import com.sperance.exileforge.rules.run.RarityBonus
import com.sperance.exileforge.rules.run.RunContext
import com.sperance.exileforge.rules.run.RunTally
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.junit.Test
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.RunState
import kotlin.test.assertEquals

/** Документ героя (1.0.0) проходит кодек Mongo туда и обратно, а ролл лежит в базе компактно - `{c,t,p}`. */
class HeroDocumentTest {

    @Test
    fun a_hero_with_a_run_survives_the_bson_codec() {
        val codec = MongoClientSettings.getDefaultCodecRegistry().get(Hero::class.java)
        val hero = Hero(userId = "u", name = "n", heroClass = "WITCH").apply {
            items += ItemInstance("i1", "IRON_HAT", Rarity.RARE, listOf(Roll("HEALTH", 2, 0.5, fractured = true, scale = 1.1)), slot = Slot.HELMET)
            items += ItemInstance("i2", "MAP_X", Rarity.MAGIC, listOf(Roll("MAP_PACK", 1, 0.25)))
            bag["CHAOS_ORB"] = 3
            tree += TakenNode("INT_START")
            tree += TakenNode("ATTR_1", 2)
            campaign.chests["Z"] = ChestWindow(10, 2)
            campaign.run = RunState("r", 42L, "Z", RunContext("WITCH", 7, mapOf("NORMAL" to RarityBonus(quantity = 5.0)), mapOf("ATLAS_GOLD" to 3.0)), 1L, 4, linkedSetOf(8, 9), tally = RunTally(chests = 2, bosses = 1), content = "h")
            overflow += ItemInstance("i3", "IRON_HAT", Rarity.MAGIC, listOf(Roll("HEALTH", 1, 0.1)))
            stashSlots = 2
        }
        val document = BsonDocument()
        codec.encode(BsonDocumentWriter(document), hero, EncoderContext.builder().build())
        val back = codec.decode(BsonDocumentReader(document), DecoderContext.builder().build())
        assertEquals(hero.items, back.items)
        assertEquals(hero.bag, back.bag)
        assertEquals(hero.tree, back.tree)
        assertEquals(hero.campaign, back.campaign)
        assertEquals(hero.overflow, back.overflow)
        assertEquals(hero.stashSlots, back.stashSlots)
        // Память прочитанного документа в базу не уходит
        assertEquals(false, document.containsKey("loaded"))
        val roll = document.getArray("items")[0].asDocument().getArray("m")[0].asDocument()
        assertEquals(setOf("c", "t", "p", "f", "x"), roll.keys)
    }
}
