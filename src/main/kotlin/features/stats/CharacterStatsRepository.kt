package features.stats

import base.repository.BaseRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class CharacterStatsRepository : BaseRepository<CharacterStats, CharacterStatsTable>(
    table = CharacterStatsTable,
    entityClass = CharacterStats::class
) {
    override val entityName = "CharacterStats"

    fun findByCharacter(characterId: Long): CharacterStats? = transaction {
        table.selectAll()
            .where { table.characterId eq characterId }
            .singleOrNull()
            ?.let(::toEntity)
    }
}
