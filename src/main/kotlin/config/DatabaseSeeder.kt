package config

import SEED_ADMIN_PASSWORD
import SEED_TEST_PLAYER_PASSWORD
import application.enums.EnumUserRoles
import com.mongodb.kotlin.client.coroutine.ClientSession
import config.MongoFactory.transactionExecute
import extensions.printLog
import features.data.auction.AuctionLotRepository
import features.data.guild.GuildEventRepository
import features.data.guild.GuildRepository
import features.data.auth.AuthSessionRepository
import features.data.blockList.BlockListRepository
import features.data.hero.Hero
import features.data.hero.HeroRepository
import features.data.idempotency.IdempotentReplyStore
import features.data.redemptionCodes.RedemptionCodes
import features.data.redemptionCodes.RedemptionCodesRepository
import features.data.redemptionCodes.RedemptionItem
import features.data.redemptionCodes.RedemptionKind
import features.data.user.User
import features.data.user.UserRepository
import features.caches.BlockListCache
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Старт базы (1.0.0): разовая очистка [DatabaseWipe], индексы, и на пустой базе - аккаунты из
 * окружения, по герою на каждый и один промокод. Справочников в базе нет: контент читает [ContentStore].
 */
object DatabaseSeeder : KoinComponent {

    private val users: UserRepository by inject()
    private val sessions: AuthSessionRepository by inject()
    private val heroes: HeroRepository by inject()
    private val lots: AuctionLotRepository by inject()
    private val guilds: GuildRepository by inject()
    private val guildEvents: GuildEventRepository by inject()
    private val blockList: BlockListRepository by inject()
    private val codes: RedemptionCodesRepository by inject()
    private val blockListCache: BlockListCache by inject()
    private val content: ContentStore by inject()

    suspend fun seed() {
        try {
            getKoin()
        } catch (e: Exception) {
            printLog("❌ Koin not initialized! Call startKoin first.")
            return
        }

        printLog("Database seeding started")
        DatabaseWipe.runOnce()
        GuildWipe.runOnce()
        ItemWipe.runOnce(content.index)
        TreeWipe.runOnce(content.index)
        TierShift.runOnce(content.index)
        CraftShift.runOnce(content.index)
        AtlasWipe.runOnce()
        ensureIndexes()

        transactionExecute { session ->
            seedUsers(session)
            seedHeroes(session)
            seedRedemptionCodes(session)
        }
        blockListCache.initializeCache()
        printLog("Database seeding completed")
    }

    /** Индексы - до транзакции: создание индекса меняет каталог MongoDB и рвёт открытую транзакцию. */
    private suspend fun ensureIndexes() = coroutineScope {
        (listOf(users, sessions, heroes, lots, guilds, guildEvents, blockList, codes).map { async { it.ensureIndexes() } } +
            async { IdempotentReplyStore.ensureIndexes() }).awaitAll()
        printLog("  → indexes ensured")
    }

    private suspend fun seedUsers(session: ClientSession) {
        if (users.count(includeDeleted = true) > 0) return
        val seeded = buildList {
            SEED_ADMIN_PASSWORD?.let { add(User(name = "Admin", email = "admin@game.com", age = 25, login = "admin", password = it, role = EnumUserRoles.ADMIN)) }
            SEED_TEST_PLAYER_PASSWORD?.let { add(User(name = "TestPlayer", email = "player@game.com", age = 22, login = "test1", password = it)) }
        }
        if (seeded.isEmpty()) { printLog("  → ADMIN_PASSWORD and TEST_PLAYER_PASSWORD are not set, no users seeded"); return }
        users.insertMany(seeded, session)
        printLog("  → ${seeded.size} users created")
    }

    /** По герою каждому сидовому игроку; администратор создаёт персонажа сам. Стартовый набор выдаёт сам репозиторий. */
    private suspend fun seedHeroes(session: ClientSession) {
        if (heroes.count(includeDeleted = true) > 0) return
        val all = users.findAll(session).filter { it.role != EnumUserRoles.ADMIN }
        if (all.isEmpty()) return
        val classes = content.index.classes.classes.map { it.code }
        all.forEachIndexed { i, user ->
            heroes.insert(Hero(userId = user._id, name = "${user.name} ${classes[i % classes.size].lowercase().replaceFirstChar(Char::uppercase)}", heroClass = classes[i % classes.size], level = 10, experience = content.index.classes.threshold(10) ?: 0.0), session)
        }
        printLog("  → ${all.size} heroes created")
    }

    private suspend fun seedRedemptionCodes(session: ClientSession) {
        if (codes.count() > 0) return
        codes.insertMany(listOf(RedemptionCodes("ALFA_BETA_GAMMA", listOf(
            RedemptionItem(RedemptionKind.EXPERIENCE, amount = 500.0),
            RedemptionItem(RedemptionKind.GOLD, amount = 100.0),
        ), "")), session)
        printLog("  → 1 redemption code created")
    }
}
