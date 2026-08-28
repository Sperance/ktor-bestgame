package features.data.redemptionCodes

import base.exception.model.CharacterExceptions
import base.exception.model.RedemptionCodesExceptions
import base.repository.BaseRepository
import config.MongoFactory.transactionExecute
import extensions.now
import features.data.character.Character
import features.data.character.CharacterRepository
import kotlinx.datetime.LocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

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