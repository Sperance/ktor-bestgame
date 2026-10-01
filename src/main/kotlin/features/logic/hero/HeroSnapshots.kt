package features.logic.hero

import com.sperance.exileforge.rules.content.AutoSell
import base.route.ApiMongoResponse
import com.sperance.exileforge.rules.RulesJson
import com.sperance.exileforge.rules.content.HeroSkills
import com.sperance.exileforge.rules.content.TakenNode
import com.sperance.exileforge.rules.content.sha256
import com.sperance.exileforge.rules.roll.ActiveWork
import com.sperance.exileforge.rules.roll.CraftsAway
import com.sperance.exileforge.rules.roll.ItemBuckets
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.ProfessionProgress
import config.ContentStore
import extensions.printLog
import features.data.hero.CampaignState
import features.data.hero.Hero
import features.data.hero.HeroRepository
import features.logic.crafts.CraftsService
import features.logic.pets.PetState
import features.logic.trade.MerchantService
import features.logic.trade.MerchantStock
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Одна часть героя: отпечаток её содержимого и само содержимое; часть без отпечатка у клиента заменяется целиком. */
@Serializable
data class HeroPart(val version: String, val data: JsonElement)

/**
 * Снимок героя: [version] - версия документа, она же `ETag` у `GET /hero/view`; в [parts] лежат только
 * части, чьих отпечатков клиент не прислал в [HeroSnapshots.HEADER], - остальные у него уже есть.
 */
@Serializable
data class HeroSnapshot(val version: String, val parts: Map<String, HeroPart>)

/** Герой без вещей, сумки, дерева и кампании - те лежат своими частями; [stashSlots] - докупленные пачки мест тайника. */
@Serializable
data class HeroView(
    val id: String, val userId: String, val name: String, val description: String, val heroClass: String, val level: Int, val experience: Double,
    val money: Long, val skills: HeroSkills, val atlas: List<String>, val earned: List<String>, val recipes: List<String>, val version: Long,
    val stashSlots: Int = 0,
    /** Летопись (1.3.0): накопленные счётчики - выводимые клиент добавит сам - и титул у имени. */
    val counters: Map<String, Long> = emptyMap(),
    val title: String = "",
    /** Фильтр добычи (1.45.0). */
    val autoSell: AutoSell = AutoSell(),
    /** План дерева (1.45.0). */
    val plannedTree: List<TakenNode> = emptyList(),
) {
    companion object {
        fun of(hero: Hero) = HeroView(hero._id, hero.userId, hero.name, hero.description, hero.heroClass, hero.level, hero.experience, hero.money,
            hero.skills, hero.atlas.toList(), hero.earned.toList(), hero.recipes.toList(), hero.version, hero.stashSlots,
            hero.counters.toMap(), hero.title, hero.autoSell, hero.plannedTree.toList())
    }
}

/** Ремёсла героя как они лежат: прогресс профессий и идущая работа; виды считает клиент правилами. */
@Serializable
data class WorkState(val professions: Map<String, ProfessionProgress> = emptyMap(), val work: ActiveWork? = null, val away: CraftsAway? = null)

/**
 * Снимки героя для ответов команд и `GET /hero/view`. Версия части - отпечаток её JSON: совпал -
 * часть не уходит. Вещи - корзинами [ItemBuckets] и порядком: новая добыча шлёт свою корзину и
 * порядок, а не весь тайник. Герой - один документ, так что снимок читает его один раз и кодирует части
 * компактным JSON правил (без значений по умолчанию).
 */
object HeroSnapshots : KoinComponent {
    /** Заголовок, в котором клиент перечисляет свои части: `hero=<отпечаток>,items=<отпечаток>`. */
    const val HEADER = "X-Hero-Parts"
    const val HERO = "hero"
    const val OVERFLOW = "overflow"
    const val BAG = "bag"
    const val TREE = "tree"
    const val CAMPAIGN = "campaign"
    const val CRAFTS = "crafts"
    const val MERCHANT = "merchant"
    const val PETS = "pets"

    private val heroes: HeroRepository by inject()
    private val crafts: CraftsService by inject()
    private val content: ContentStore by inject()
    private val merchant: MerchantService by inject()

    /** Части, которые клиент назвал в [HEADER]; битый заголовок значит «ничего нет». */
    fun known(header: String?): Map<String, String> =
        header.orEmpty().split(',').mapNotNull { pair ->
            val name = pair.substringBefore('=', "").trim()
            val hash = pair.substringAfter('=', "").trim()
            if (name.isEmpty() || hash.isEmpty()) null else name to hash
        }.toMap()

    /**
     * Снимок героя сейчас; работа ремесла досчитывается первой, иначе сумка отстала бы от добытого, а копии
     * сверяются с контентом (1.30.0): клиент видит вещь такой, какой её выдаст запись.
     */
    suspend fun of(heroId: String, known: Map<String, String> = emptyMap()): HeroSnapshot {
        var hero = heroes.requireHero(heroId, "heroView")
        if (heroes.reconcile(hero)) hero = heroes.save(hero, "heroView")
        crafts.settle(hero)
        return of(hero, known)
    }

    fun of(hero: Hero, known: Map<String, String>): HeroSnapshot {
        val parts = LinkedHashMap<String, HeroPart>()
        fun <T> part(name: String, serializer: KSerializer<T>, value: T) {
            val json = RulesJson.encodeToJsonElement(serializer, value)
            val hash = sha256(json.toString()).take(16)
            if (known[name] != hash) parts[name] = HeroPart(hash, json)
        }
        part(HERO, HeroView.serializer(), HeroView.of(hero))
        val buckets = hero.items.groupBy { ItemBuckets.of(it.id) }
        ItemBuckets.names.forEachIndexed { bucket, name -> part(name, ListSerializer(ItemInstance.serializer()), buckets[bucket].orEmpty()) }
        part(ItemBuckets.ORDER, ListSerializer(String.serializer()), hero.items.map { it.id })
        part(OVERFLOW, ListSerializer(ItemInstance.serializer()), hero.overflow)
        part(BAG, MapSerializer(String.serializer(), Long.serializer()), hero.bag)
        part(TREE, ListSerializer(TakenNode.serializer()), hero.tree)
        part(CAMPAIGN, CampaignState.serializer(), hero.campaign)
        part(CRAFTS, WorkState.serializer(), WorkState(hero.professions, hero.work, hero.craftsAway))
        part(MERCHANT, MerchantStock.serializer(), merchant.current(hero))
        part(PETS, PetState.serializer(), PetState.of(hero, content.index.rules.pets.cap))
        return HeroSnapshot(hero.version.toString(), parts)
    }

    /** Снимок для ответа команды: команда уже прошла, сбой снимка её не отменяет - клиент перечитает героя сам. */
    suspend fun afterCommand(heroId: String?, header: String?): HeroSnapshot? {
        if (heroId.isNullOrBlank()) return null
        return try { of(heroId, known(header)) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { printLog("[HeroSnapshots] $heroId: ${e.message}"); null }
    }

    /** Снимок, если клиент его просил: заголовок [HEADER] есть, пусть и пустой (`none`). */
    suspend fun forCall(call: ApplicationCall): HeroSnapshot? {
        val header = call.request.headers[HEADER] ?: return null
        return afterCommand(call.heroContext?.heroId, header)
    }
}

/** Ответ команды героя: данные и рядом снимок героя после неё. */
suspend inline fun <reified T> ApplicationCall.respondWithHero(data: T) =
    respond(ApiMongoResponse(success = true, data = data, hero = HeroSnapshots.forCall(this)))
