package features.data.redemptionCodes

import base.exception.model.RedemptionCodesExceptions
import base.repository.BaseRepository
import base.repository.IndexSpec
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.ClientSession
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import config.ContentStore
import config.MongoFactory.transactionExecute
import extensions.now
import features.data.hero.Hero
import features.data.hero.HeroRepository
import features.data.user.UserRepository
import features.logic.hero.Rewards
import features.logic.hero.Stash
import kotlinx.datetime.LocalDateTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class RedemptionCodesRepository : BaseRepository<RedemptionCodes>(RedemptionCodes::class), KoinComponent {
    private val heroes: HeroRepository by inject()
    private val users: UserRepository by inject()
    private val content: ContentStore by inject()
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

    /**
     * Активация: код принадлежит аккаунту, а не герою, - отметка на аккаунте ложится одной условной
     * записью в той же транзакции, что и награда, поэтому из двух параллельных активаций проходит одна.
     */
    suspend fun redeem(heroId: String, code: String): String {
        val method = "redeem"
        val hero = heroes.requireHero(heroId, method)
        val redemption = findByField(RedemptionCodes::code, code) ?: throw RedemptionCodesExceptions.funExceptionNotFoundRedemption(method, code)
        if (redemption._id in (users.findById(hero.userId)?.redeemedCodes.orEmpty())) throw RedemptionCodesExceptions.funExceptionRedemptionAlreadyUser(method, code)
        if (redemption.expiredAt != null && redemption.expiredAt!! < LocalDateTime.now()) throw RedemptionCodesExceptions.funExceptionRedemptionExpired(method, code)
        if (redemption.treasure.isEmpty()) throw RedemptionCodesExceptions.funExceptionEmptyTreasure(method, code)
        grant(hero, redemption.treasure)
        transactionExecute(method) { session ->
            if (!users.claimRedemption(hero.userId, redemption._id, session)) throw RedemptionCodesExceptions.funExceptionRedemptionAlreadyUser(method, code)
            heroes.update(hero, session)
            // Счётчик растёт в самой базе: две активации разом не теряются
            collection.updateOne(session, Filters.eq("_id", redemption._id), Updates.inc("used", 1L))
        }
        return "system.success"
    }

    private fun grant(hero: Hero, treasure: List<RedemptionItem>) {
        val factory = ItemFactory(index)
        val dice = Dice.system()
        treasure.forEach { reward ->
            when (reward.kind) {
                RedemptionKind.ITEM -> hero.earn(reward.item, reward.amount.toLong())
                RedemptionKind.EXPERIENCE -> Rewards.addExperience(hero, reward.amount, index)
                RedemptionKind.GOLD -> hero.gain(reward.amount.toLong())
                // Самоцвет герою ниже `loot.jewelHeroLevel` не выдаётся - как из любого другого источника
                RedemptionKind.EQUIPMENT -> index.template(reward.item)?.takeIf { index.rules.loot.obtainable(it, hero.level) }?.let { template ->
                    Stash.receive(hero, List(reward.amount.toInt()) { factory.create(Hero.newItemId(), template, template.rarity, dice, level = index.rules.loot.itemLevel(hero.level)) }, index)
                }
            }
        }
    }
}
