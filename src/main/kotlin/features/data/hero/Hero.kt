package features.data.hero

import base.entity.TrackedEntity
import base.entity.VersionedEntity
import base.exception.model.CharacterExceptions
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.Pet
import com.sperance.exileforge.rules.content.QuestLog
import com.sperance.exileforge.rules.content.QuestProgress
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
import com.sperance.exileforge.rules.run.RunTally
import extensions.now
import features.logic.trade.MerchantStock
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.bson.BsonValue
import org.bson.types.ObjectId

/**
 * Герой одним документом (1.0.0): вещи, сумка, дерево, умения, атлас, ремёсла, торговец и кампания -
 * внутри. Одна запись с проверкой версии закрывает любую команду; справочников документ не несёт -
 * копия вещи хранит только шаблон, редкость и роллы `{c,t,p}`, всё остальное выводит контент.
 * Копий не больше мест тайника ([features.logic.hero.Stash]), запись шлёт только изменившиеся поля.
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
    /** Докупленные пачки мест тайника. */
    var stashSlots: Int = 0,
    /** Вещи, которым не хватило места в тайнике: ждут, пока герой их заберёт или продаст. */
    var overflow: MutableList<ItemInstance> = mutableListOf(),
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
    /** Летопись героя (1.3.0): счётчики [Counter] - сумма или рекорд. */
    var counters: MutableMap<String, Long> = mutableMapOf(),
    /** Титул у имени - код одного из открытых достижениями; пусто - без титула. */
    var title: String = "",
    /** Зверинец (1.5.0): питомцы героя и активные - боевой и помощник, по id; пусто - никого. */
    var pets: MutableList<Pet> = mutableListOf(),
    var petCombat: String = "",
    var petHelper: String = "",
    var campaign: CampaignState = CampaignState(),
    /** Гильдия героя (1.20.0): копия членства без чтения гильдии; null - не состоит. */
    var guild: HeroGuild? = null,
    /** Когда герой вышел или был исключён (мс эпохи): вступить снова можно через `rejoinHours` правил. */
    var guildLeftAt: Long = 0,
    /** Задания (1.21.0): ежедневные, недельные, контракты, сюжет и гильдейские; прогресс двигает [count]. */
    var quests: QuestLog = QuestLog(),
    /** Поток наград захода (1.30.0): его семя клиенту не отдаётся никогда - ни снимком, ни общим CRUD. */
    var rewards: RewardStream = RewardStream(),
    /** Рекорды производных заданий (1.30.0): сколько узлов дерева, атласа и пройденных зон у героя бывало. */
    var peaks: MutableMap<String, Long> = mutableMapOf(),
    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity, TrackedEntity {

    @Transient override var loaded: Map<String, BsonValue>? = null

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

    /** Счётчик летописи: сумма или рекорд - как велит его вид; тот же прирост двигает задания героя в зоне открытого захода. */
    fun count(counter: String, amount: Long = 1) {
        Counter.add(counters, counter, amount)
        QuestProgress.advance(quests, counter, amount, campaign.run?.zone, guild?.id, System.currentTimeMillis())
    }

    /** Счётчики вместе с выводимыми из героя: уровень, пройденные зоны, узлы атласа. */
    fun chronicle(): Map<String, Long> = Counter.values(counters, level, campaign.cleared.size, atlas.size)

    /** Золото герою - в кошелёк и в летопись. */
    fun gain(gold: Long) {
        if (gold <= 0) return
        money += gold
        count(Counter.GOLD_EARNED, gold)
    }

    /** Золото от героя: списано и записано в летопись; хватает ли - проверяет вызывающий. */
    fun pay(gold: Long) {
        money -= gold
        count(Counter.GOLD_SPENT, gold)
    }

    fun pet(id: String): Pet? = pets.firstOrNull { it.id == id }

    /** Питомцы в деле: боевой и помощник. */
    fun activePets(): List<Pet> = pets.filter { it.id == petCombat || it.id == petHelper }

    fun replacePet(pet: Pet) {
        val at = pets.indexOfFirst { it.id == pet.id }
        if (at >= 0) pets[at] = pet
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
 * Поток наград героя (1.30.0): [seed] катится один раз при первой награде, [drawn] - сколько наград
 * уже выдано. Награды захода берут кости отсюда, а не из семени захода: клиент их не предскажет.
 */
@Serializable
data class RewardStream(var seed: Long = 0, var drawn: Long = 0) {
    /** Кости наград; семя появляется при первом обращении. */
    fun draws(): com.sperance.exileforge.rules.run.RewardDraws {
        while (seed == 0L) seed = java.security.SecureRandom().nextLong()
        return com.sperance.exileforge.rules.run.RewardDraws(seed, drawn)
    }
}

/**
 * Членство героя в гильдии, продублированное из её документа: [level] гильдии и индекс ранга [rank] героя
 * гильдия переписывает сама, когда они меняются, - задания гильдии читают только героя.
 */
@Serializable
data class HeroGuild(val id: String, val level: Int = 1, val rank: Int = 0)

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
    /** Когда герой последний раз получил новое семя захода, мс эпохи: чаще правила `run.newSeedSeconds` - нельзя. */
    var seededAt: Long = 0,
)

/**
 * Открытый заход: семя и контекст, по которым сервер проигрывает журнал клиента, [applied] - сколько
 * событий уже принято (каждый номер один раз), [killed] и [vaalKilled] - убитые жетоны `i*8+m`,
 * [tally] - сколько наград каждого вида выдано, [content] - отпечаток контента, по которому заход
 * катится: сменился контент - журнал уже не проиграть так, как его видел клиент.
 */
@Serializable
data class RunState(
    val id: String,
    val seed: Long,
    val zone: String,
    val context: RunContext,
    val startedAt: Long,
    var applied: Int = 0,
    var killed: MutableSet<Int> = linkedSetOf(),
    var vaalKilled: MutableSet<Int> = linkedSetOf(),
    val tally: RunTally = RunTally(),
    val content: String = "",
)
