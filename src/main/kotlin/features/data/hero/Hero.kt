package features.data.hero

import base.entity.VersionedEntity
import base.exception.model.CharacterExceptions
import com.sperance.exileforge.rules.content.TakenNode
import com.sperance.exileforge.rules.content.HeroSkills
import com.sperance.exileforge.rules.roll.AbyssRun
import com.sperance.exileforge.rules.roll.AbyssWindow
import com.sperance.exileforge.rules.roll.ActiveMap
import com.sperance.exileforge.rules.roll.ActiveWork
import com.sperance.exileforge.rules.roll.ChestWindow
import com.sperance.exileforge.rules.roll.CrystalWindow
import com.sperance.exileforge.rules.roll.ItemInstance
import com.sperance.exileforge.rules.roll.ProfessionProgress
import com.sperance.exileforge.rules.roll.VaalZone
import com.sperance.exileforge.rules.run.RunContext
import extensions.now
import features.logic.trade.MerchantStock
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Герой одним документом (1.0.0): вещи, сумка, дерево, умения, атлас, ремёсла, торговец и кампания -
 * внутри. Одна запись с проверкой версии закрывает любую команду; справочников документ не несёт -
 * копия вещи хранит только шаблон, редкость и роллы `{c,t,p}`, всё остальное выводит контент.
 */
@Serializable
data class Hero(
    var userId: String,
    var name: String,
    var description: String = "",
    var heroClass: String = "",
    var level: Int = 1,
    var experience: Double = 0.0,
    var money: Long = 0,
    /** Все копии вещей героя: надетые ([ItemInstance.slot]), в гнёздах ([ItemInstance.socket]) и в тайнике. */
    var items: MutableList<ItemInstance> = mutableListOf(),
    /** Сумка: код предмета - сколько. */
    var bag: MutableMap<String, Long> = mutableMapOf(),
    var tree: MutableList<TakenNode> = mutableListOf(),
    var skills: HeroSkills = HeroSkills(),
    /** Взятые узлы атласа без корня и засчитанные достижения `<вид>:<зона>`. */
    var atlas: MutableList<String> = mutableListOf(),
    var earned: MutableList<String> = mutableListOf(),
    /** Известные рецепты верстака. */
    var recipes: MutableList<String> = mutableListOf(),
    var professions: MutableMap<String, ProfessionProgress> = mutableMapOf(),
    var work: ActiveWork? = null,
    var merchant: MerchantStock? = null,
    /** Мест под лоты аукциона докуплено сверх базовых. */
    var auctionSlots: Int = 0,
    var campaign: CampaignState = CampaignState(),
    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity {

    fun item(id: String): ItemInstance? = items.firstOrNull { it.id == id }

    /** Копия героя или «не найдена»: чужая и несуществующая отвечают одинаково. */
    fun requireItem(id: String, method: String): ItemInstance = item(id) ?: throw CharacterExceptions.funExceptionItemNotFound(method, id)

    /** Всё, что работает на герое: надетое и вставленное в гнёзда. */
    val equipped: List<ItemInstance> get() = items.filter { it.slot != null }

    /** Гнёзда дерева, занятые самоцветами. */
    fun sockets(): Set<String> = items.mapNotNullTo(HashSet()) { it.socket }

    /** Списывает из сумки; нехватка - отказ `CH_009`. */
    fun spend(code: String, amount: Long, method: String) {
        if (amount <= 0) throw CharacterExceptions.funExceptionItemLowZero(method, "$code:$amount")
        val owned = bag[code] ?: 0L
        if (owned < amount) throw CharacterExceptions.funExceptionItemLowZero(method, "$code:$owned")
        if (owned == amount) bag.remove(code) else bag[code] = owned - amount
    }

    /** Кладёт в сумку до потолка стопки [cap]: излишек сгорает, а не срывает выдачу. */
    fun earn(code: String, amount: Long, cap: Long) {
        if (amount <= 0) return
        val owned = bag[code] ?: 0L
        bag[code] = (owned + amount).coerceAtMost(maxOf(cap, owned))
    }

    fun replace(item: ItemInstance) {
        val at = items.indexOfFirst { it.id == item.id }
        if (at < 0) items += item else items[at] = item
    }

    companion object {
        fun newItemId(): String = ObjectId().toHexString()
    }
}

/**
 * Кампания героя: пройденные зоны, окна сундуков, кристаллов и расщелин по зонам, боссы (когда
 * вернутся, мс эпохи), карта захода, Ваал-зона, спуск в Бездну и открытый заход [run].
 */
@Serializable
data class CampaignState(
    var cleared: MutableList<String> = mutableListOf(),
    var chests: MutableMap<String, ChestWindow> = mutableMapOf(),
    var bosses: MutableMap<String, Long> = mutableMapOf(),
    var crystals: MutableMap<String, CrystalWindow> = mutableMapOf(),
    var abyss: MutableMap<String, AbyssWindow> = mutableMapOf(),
    var activeMap: ActiveMap? = null,
    var vaalZone: VaalZone? = null,
    var abyssRun: AbyssRun? = null,
    var recipeRolled: Boolean = false,
    var corruptionOpened: Boolean = false,
    var run: RunState? = null,
)

/**
 * Открытый заход: семя и контекст, по которым сервер проигрывает журнал клиента, [applied] - сколько
 * событий уже принято (каждый номер один раз), [killed] и [vaalKilled] - убитые жетоны `i*8+m`.
 */
@Serializable
data class RunState(
    val id: String,
    val seed: Long,
    val zone: String,
    val context: RunContext,
    val startedAt: Long,
    var applied: Int = 0,
    var killed: MutableList<Int> = mutableListOf(),
    var vaalKilled: MutableList<Int> = mutableListOf(),
)
