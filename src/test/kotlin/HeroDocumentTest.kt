import com.mongodb.MongoClientSettings
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.TakenNode
import com.sperance.exileforge.rules.roll.ChestWindow
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.Roll
import com.sperance.exileforge.rules.run.RarityBonus
import com.sperance.exileforge.rules.run.RunContext
import features.data.hero.Hero
import features.data.hero.RunState
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.junit.Test
import kotlin.test.assertEquals

/** Документ героя (1.0.0) проходит кодек Mongo туда и обратно, а ролл лежит в базе компактно - `{c,t,p}`. */
class HeroDocumentTest {

    @Test
    fun a_hero_with_a_run_survives_the_bson_codec() {
        val codec = MongoClientSettings.getDefaultCodecRegistry().get(Hero::class.java)
        val hero = Hero(userId = "u", name = "n", heroClass = "WITCH").apply {
            items += ItemInstance("i1", "IRON_HAT", Rarity.RARE, listOf(Roll("HEALTH", 2, 0.5, fractured = true, scale = 1.1)), slot = Slot.HELMET)
            items += ItemInstance("i2", "MAP_X", Rarity.UNCOMMON, listOf(Roll("MAP_PACK", 1, 0.25)))
            bag["CHAOS_ORB"] = 3
            tree += TakenNode("INT_START"); tree += TakenNode("ATTR_1", 2)
            campaign.chests["Z"] = ChestWindow(10, 2)
            campaign.run = RunState("r", 42L, "Z", RunContext("WITCH", 7, mapOf("NORMAL" to RarityBonus(quantity = 5.0)), mapOf("ATLAS_GOLD" to 3.0)), 1L, 4, mutableListOf(8, 9))
        }
        val document = BsonDocument()
        codec.encode(BsonDocumentWriter(document), hero, EncoderContext.builder().build())
        val back = codec.decode(BsonDocumentReader(document), DecoderContext.builder().build())
        assertEquals(hero.items, back.items)
        assertEquals(hero.bag, back.bag)
        assertEquals(hero.tree, back.tree)
        assertEquals(hero.campaign, back.campaign)
        val roll = document.getArray("items")[0].asDocument().getArray("m")[0].asDocument()
        assertEquals(setOf("c", "t", "p", "f", "x"), roll.keys)
    }
}
