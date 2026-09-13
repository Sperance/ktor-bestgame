package ru.descend.features.redemptioncodes.persistence

import kotlinx.datetime.LocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.redemptioncodes.model.RedemptionCodes
import ru.descend.infrastructure.mongo.BaseRepository
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute
import ru.descend.shared.error.model.CharacterExceptions
import ru.descend.shared.error.model.RedemptionCodesExceptions
import ru.descend.shared.extensions.now

class RedemptionCodesRepository : BaseRepository<RedemptionCodes>(entityClass = RedemptionCodes::class), KoinComponent {
    private val characterRepository: CharacterRepository by inject()

    @Deprecated("Use the versioned redemption command")
    suspend fun useCharacterRedemptionCode(characterId: String, redemptionCode: String): String = ru.descend.shared.http.invalid("Use the versioned redemption command")
}
