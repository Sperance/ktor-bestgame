package ru.descend.exileforge.features.data.redemptionCodes

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.ContentIndex
import ru.descend.exileforge.base.exception.model.RedemptionCodesExceptions
import ru.descend.exileforge.base.repository.BaseRepository
import ru.descend.exileforge.base.repository.IndexSpec
import ru.descend.exileforge.config.ContentStore

/** Промокоды: документ с проверенной наградой. Активация героем - в [ru.descend.exileforge.features.logic.redemption.RedemptionService]. */
class RedemptionCodesRepository(private val content: ContentStore) : BaseRepository<RedemptionCodes>(RedemptionCodes::class) {
    private val index: ContentIndex get() = content.index

    override val indexes = listOf(IndexSpec.unique("idx_unique_code", "code"))

    /** Код уникален, награда проверена здесь: пустой или отрицательный подарок не доживает до игрока. */
    override suspend fun validateBeforeInsert(entity: RedemptionCodes, session: ClientSession) {
        val method = "validateBeforeInsert"
        if (entity.code.isBlank()) throw RedemptionCodesExceptions.funException(method, entity.code)
        if (findByField(RedemptionCodes::code, entity.code) != null) throw RedemptionCodesExceptions.funExceptionCodeExists(method, entity.code)
        if (entity.treasure.isEmpty()) throw RedemptionCodesExceptions.funExceptionEmptyTreasure(method, entity.code)
        entity.treasure.forEach {
            if (it.amount <= 0) throw RedemptionCodesExceptions.funExceptionRewardAmount(method, it.amount.toString())
            when (it.kind) {
                RedemptionKind.ITEM -> index.item(it.item) ?: throw RedemptionCodesExceptions.funExceptionUnknownEquipment(method, it.item)
                RedemptionKind.EQUIPMENT -> index.template(it.item) ?: throw RedemptionCodesExceptions.funExceptionUnknownEquipment(method, it.item)
                else -> Unit
            }
        }
    }

    /** Счётчик активаций растёт в самой базе: две активации разом не теряются. */
    suspend fun countUse(id: String, session: ClientSession) {
        collection.updateOne(session, Filters.eq("_id", id), Updates.inc("used", 1L))
    }
}
