package ru.descend.exileforge.features.logic.redemption

import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import kotlinx.datetime.LocalDateTime
import ru.descend.exileforge.base.exception.model.RedemptionCodesExceptions
import ru.descend.exileforge.config.ContentStore
import ru.descend.exileforge.config.MongoFactory.transactionExecute
import ru.descend.exileforge.extensions.now
import ru.descend.exileforge.features.data.hero.Hero
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.data.redemptionCodes.RedemptionCodes
import ru.descend.exileforge.features.data.redemptionCodes.RedemptionCodesRepository
import ru.descend.exileforge.features.data.redemptionCodes.RedemptionItem
import ru.descend.exileforge.features.data.redemptionCodes.RedemptionKind
import ru.descend.exileforge.features.data.user.UserRepository
import ru.descend.exileforge.features.logic.hero.Rewards
import ru.descend.exileforge.features.logic.hero.Stash

/** Активация промокода героем: отметка на аккаунте и награда в одной транзакции. */
class RedemptionService(
    private val codes: RedemptionCodesRepository,
    private val heroes: HeroRepository,
    private val users: UserRepository,
    private val content: ContentStore,
) {
    private val index: ContentIndex get() = content.index

    /**
     * Активация: код принадлежит аккаунту, а не герою, - отметка на аккаунте ложится одной условной
     * записью в той же транзакции, что и награда, поэтому из двух параллельных активаций проходит одна.
     */
    suspend fun redeem(heroId: String, code: String): String {
        val method = "redeem"
        val hero = heroes.requireHero(heroId, method)
        val redemption = codes.findByField(RedemptionCodes::code, code) ?: throw RedemptionCodesExceptions.funExceptionNotFoundRedemption(method, code)
        if (redemption._id in (users.findById(hero.userId)?.redeemedCodes.orEmpty())) throw RedemptionCodesExceptions.funExceptionRedemptionAlreadyUser(method, code)
        if (redemption.expiredAt != null && redemption.expiredAt!! < LocalDateTime.now()) throw RedemptionCodesExceptions.funExceptionRedemptionExpired(method, code)
        if (redemption.treasure.isEmpty()) throw RedemptionCodesExceptions.funExceptionEmptyTreasure(method, code)
        grant(hero, redemption.treasure)
        transactionExecute(method) { session ->
            if (!users.claimRedemption(hero.userId, redemption._id, session)) throw RedemptionCodesExceptions.funExceptionRedemptionAlreadyUser(method, code)
            heroes.update(hero, session)
            codes.countUse(redemption._id, session)
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
