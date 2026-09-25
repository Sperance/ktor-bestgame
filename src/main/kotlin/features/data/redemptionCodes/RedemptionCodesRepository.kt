package features.data.redemptionCodes

import base.exception.model.CharacterExceptions
import base.exception.model.RedemptionCodesExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.now
import features.data.character.Character
import features.data.character.CharacterRepository
import features.data.character.character_data.CharacterItems
import features.data.character.character_data.GainedRedemtionCodes
import features.caches.EquipmentCache
import features.data.inventory.CharacterEquipmentRepository
import features.data.user.UserRepository
import kotlinx.datetime.LocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RedemptionCodesRepository : BaseRepository<RedemptionCodes>(entityClass = RedemptionCodes::class), KoinComponent {
    private val characterRepository: CharacterRepository by inject()
    private val characterEquipmentRepository: CharacterEquipmentRepository by inject()
    private val equipmentCache: EquipmentCache by inject()
    private val userRepository: UserRepository by inject()

    // Погашение ищет код по строке, которую ввёл игрок
    override val indexes = listOf(IndexSpec.unique("idx_unique_code", "code"))

    override val retiredIndexes = listOf("code_1")

    /**
     * Что администратор не имеет права создать.
     *
     * Код уникален, потому что игрок вводит его с клавиатуры, а не выбирает: два
     * документа с одной строкой сделали бы активацию лотереей. Награда проверяется
     * здесь, а не при активации, чтобы пустой или отрицательный подарок не дожил до
     * игрока, который его уже ввёл.
     */
    override suspend fun validateBeforeInsert(entity: RedemptionCodes, session: ClientSession) {
        val method = "validateBeforeInsert"
        if (entity.code.isBlank()) throw RedemptionCodesExceptions.funException(method, entity.code)
        if (findByField(RedemptionCodes::code, entity.code) != null)
            throw RedemptionCodesExceptions.funExceptionCodeExists(method, entity.code)
        if (entity.treasure.isEmpty()) throw RedemptionCodesExceptions.funExceptionEmptyTreasure(method, entity.code)
        entity.treasure.forEach {
            if (it.amount <= 0) throw RedemptionCodesExceptions.funExceptionRewardAmount(method, it.amount.toString())
        }
    }

    /**
     * Активация промокода: одна проверка, одна транзакция, одна награда.
     *
     * Код принадлежит **учётной записи**, а не персонажу. Отметка об активации
     * по-прежнему лежит на персонаже - там её видно рядом с тем, кому пришла награда, -
     * но ищется она по всем персонажам аккаунта: иначе владелец трёх героев забрал бы
     * одну и ту же награду трижды.
     *
     * Награда выдаётся целиком или не выдаётся вовсе. Поэтому опыт, золото и предметы
     * ложатся на уже прочитанного персонажа без собственных транзакций (см.
     * [CharacterRepository.applyExperience] и [CharacterRepository.applyItems]), а
     * экипировка создаётся в той же сессии: половина награды и списанный код - худший
     * из возможных исходов.
     */
    suspend fun useCharacterRedemptionCode(characterId: String, redemptionCode: String): String {
        val character = characterRepository.requireCharacter(characterId, "useCharacterRedemptionCode")

        val redemption = findByField(RedemptionCodes::code, redemptionCode)
            ?: throw RedemptionCodesExceptions.funExceptionNotFoundRedemption("useCharacterRedemptionCode", redemptionCode)

        if (accountUsed(character.userId, redemption._id))
            throw RedemptionCodesExceptions.funExceptionRedemptionAlreadyUser("useCharacterRedemptionCode", redemptionCode)

        if (redemption.expiredAt != null && redemption.expiredAt!! < LocalDateTime.now())
            throw RedemptionCodesExceptions.funExceptionRedemptionExpired("useCharacterRedemptionCode", redemptionCode)

        if (redemption.treasure.isEmpty())
            throw RedemptionCodesExceptions.funExceptionEmptyTreasure("useCharacterRedemptionCode", redemptionCode)

        character.gainedRedemptionCodes.add(GainedRedemtionCodes(redemption._id, LocalDateTime.now()))

        transactionExecute("useCharacterRedemptionCode") { session ->
            // Отметка на аккаунте - в той же транзакции: второй персонаж того же игрока,
            // пришедший параллельно, или новый вместо удалённого код уже не возьмут
            if (!userRepository.claimRedemption(character.userId, redemption._id, session))
                throw RedemptionCodesExceptions.funExceptionRedemptionAlreadyUser("useCharacterRedemptionCode", redemptionCode)
            grant(character, redemption.treasure, session)
            characterRepository.update(character, session)
            // Счётчик растёт в самой базе: прочитать, прибавить и записать обратно значило бы
            // терять активации, сделанные двумя игроками одновременно.
            collection.updateOne(session, Filters.eq("_id", redemption._id), Updates.inc("used", 1L))
        }

        return "system.success"
    }

    /**
     * Брал ли этот код кто-нибудь из персонажей учётной записи: один подсчёт в базе по индексу
     * `userId`, без чтения самих персонажей.
     */
    private suspend fun accountUsed(userId: String, redemptionId: String): Boolean =
        characterRepository.count(Filters.and(Filters.eq("userId", userId), Filters.eq("gainedRedemptionCodes.redemptionCodeId", redemptionId))) > 0

    /**
     * Выдача награды на уже прочитанного персонажа, внутри чужой транзакции.
     *
     * Стакающиеся предметы складываются в один вызов, потому что два подарка одной и
     * той же сферы - это одна строка сумки, а не две.
     */
    private suspend fun grant(character: Character, treasure: List<RedemptionItem>, session: ClientSession) {
        val method = "useCharacterRedemptionCode"
        treasure.forEach {
            if (it.amount <= 0) throw RedemptionCodesExceptions.funExceptionRewardAmount(method, it.amount.toString())
        }

        val items = treasure.filter { it.kind == RedemptionKind.ITEM }
            .groupBy { it.itemId }
            .map { (itemId, rows) -> CharacterItems(itemId, rows.sumOf { it.amount }.toLong()) }
        if (items.isNotEmpty()) characterRepository.applyItems(character, items, method)

        treasure.filter { it.kind == RedemptionKind.EXPERIENCE }
            .forEach { characterRepository.applyExperience(character, it.amount, method) }

        treasure.filter { it.kind == RedemptionKind.GOLD }
            .forEach { character.money += it.amount.toLong() }

        // Экипировка не стакается: каждая копия шаблона роллится заново и становится
        // отдельным документом инвентаря, поэтому количество здесь - это число копий.
        val equipment = treasure.filter { it.kind == RedemptionKind.EQUIPMENT }.flatMap { reward ->
            val template = equipmentCache.findById(reward.itemId)
                ?: throw RedemptionCodesExceptions.funExceptionUnknownEquipment(method, reward.itemId)
            List(reward.amount.toInt()) { template }
        }
        characterEquipmentRepository.addAllFromEquipment(character._id, equipment, session)
    }
}
