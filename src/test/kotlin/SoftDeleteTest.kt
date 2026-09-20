import base.repository.SoftDelete
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Filters
import org.bson.BsonDocument
import org.bson.conversions.Bson
import org.junit.Test

/**
 * Мягкое удаление: каким получается фильтр обычного чтения.
 * Mongo не нужна - фильтр разворачивается в документ на месте.
 */
class SoftDeleteTest {

    private fun Bson.render(): BsonDocument =
        toBsonDocument(BsonDocument::class.java, MongoClientSettings.getDefaultCodecRegistry())

    @Test
    fun a_plain_read_hides_softly_deleted_documents() {
        val rendered = SoftDelete.readFilter().render()

        assert(rendered.keys == setOf("deleted")) { "got $rendered" }
        assert(rendered.getDocument("deleted").getBoolean("\$ne").value) { "got $rendered" }
    }

    @Test
    fun the_condition_lets_through_documents_without_the_field() {
        // Поле deleted есть только у VersionedEntity. У справочников его нет
        // вовсе, и eq(false) выкосил бы их целиком - поэтому здесь ne(true)
        val rendered = SoftDelete.readFilter().render()

        assert(rendered.getDocument("deleted").containsKey("\$ne")) {
            "ожидалось \$ne, иначе документы без поля deleted пропадут: $rendered"
        }
        assert(!rendered.getDocument("deleted").containsKey("\$eq")) { "got $rendered" }
    }

    @Test
    fun an_own_filter_is_kept_and_the_condition_is_added_to_it() {
        val rendered = SoftDelete.readFilter(Filters.eq("characterId", "abc")).render()
        val conditions = rendered.getArray("\$and").map { it.asDocument() }

        assert(conditions.size == 2) { "got $conditions" }
        assert(conditions.any { it.containsKey("characterId") }) { "свой фильтр потерялся: $conditions" }
        assert(conditions.any { it.containsKey("deleted") }) { "условие удаления потерялось: $conditions" }
    }

    @Test
    fun asking_for_deleted_ones_leaves_the_filter_alone() {
        val own = Filters.eq("characterId", "abc")
        val rendered = SoftDelete.readFilter(own, includeDeleted = true).render()

        assert(rendered == own.render()) { "got $rendered" }
        assert(!rendered.containsKey("\$and")) { "лишнее условие: $rendered" }
    }

    @Test
    fun asking_for_everything_reads_the_whole_collection() {
        val rendered = SoftDelete.readFilter(includeDeleted = true).render()
        assert(rendered.isEmpty()) { "got $rendered" }
    }
}
