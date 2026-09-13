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

    suspend fun useCharacterRedemptionCode(characterId: String, redemptionCode: String): String {
        val character = characterRepository.findById(characterId)
        if (character == null) throw CharacterExceptions.funExceptionNotFound("useCharacterRedemptionCode", characterId)

        val redemption = findByField(RedemptionCodes::code, redemptionCode)
        if (redemption == null) throw RedemptionCodesExceptions.funExceptionNotFoundRedemption("useCharacterRedemptionCode", redemptionCode)

        if (character.gainedRedemptionCodes.find { it.redemptionCodeId == redemption._id } != null)
            throw RedemptionCodesExceptions.funExceptionRedemptionAlreadyUser("useCharacterRedemptionCode", redemptionCode)

        if (redemption.expiredAt != null && redemption.expiredAt!! < LocalDateTime.now())
            throw RedemptionCodesExceptions.funExceptionRedemptionExpired("useCharacterRedemptionCode", redemptionCode)

        redemption.used++

        //TODO Пока без добавления предметов в инвентарь персонажа
        transactionExecute { session ->
            update(redemption, session)
        }

        return "Success"
    }
}
