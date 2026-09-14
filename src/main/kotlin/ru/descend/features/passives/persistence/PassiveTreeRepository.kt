package ru.descend.features.passives.persistence

import com.mongodb.client.model.Filters
import com.mongodb.client.model.UpdateOptions
import com.mongodb.client.model.Updates
import ru.descend.features.passives.domain.*
import ru.descend.features.passives.model.PassiveTree
import ru.descend.infrastructure.mongo.BaseRepository
import ru.descend.shared.http.missing

class PassiveTreeRepository : BaseRepository<PassiveTreeDocument>(PassiveTreeDocument::class) {
    suspend fun seed() {
        PassiveRules(PassiveTreeSeed.tree)
        collection.updateOne(Filters.eq("_id", "shared:1"), Updates.setOnInsert("tree", PassiveTreeSeed.tree), UpdateOptions().upsert(true))
    }
    suspend fun definition(revision: Int): PassiveTree {
        if (revision <= 0) missing()
        var document = findById("shared:$revision")
        if (document == null && revision == 1) { seed(); document = findById("shared:1") }
        return (document ?: missing()).tree.also { require(it.revision == revision); PassiveRules(it) }
    }
}
