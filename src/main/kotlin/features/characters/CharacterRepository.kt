package features.characters

import base.repository.BaseRepository
import features.post.Post
import features.post.PostsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class CharacterRepository : BaseRepository<Character, CharacterTable>(
    table = CharacterTable,
    entityClass = Character::class
) {
    override val entityName = "Character"
}