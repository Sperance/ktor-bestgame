package features.characters

import base.repository.BaseRepository

class CharacterRepository : BaseRepository<Character, CharacterTable>(
    table = CharacterTable,
    entityClass = Character::class
) {
    override val entityName = "Character"
}