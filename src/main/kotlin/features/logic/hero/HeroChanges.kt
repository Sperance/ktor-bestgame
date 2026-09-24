package features.logic.hero

import features.data.inventory.CharacterEquipment
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Журнал изменений инвентаря за один запрос (с 0.49.0).
 *
 * Репозиторий инвентаря пишет сюда каждую закоммиченную вставку, правку и удаление, а
 * репозиторий персонажа - каждый сдвиг `inventoryRevision`. По ним снимок героя отвечает
 * дельтой - только тем, что этот запрос тронул, - вместо всего списка.
 * Живёт в контексте корутины запроса, см. [installHeroChanges].
 */
class HeroChanges : AbstractCoroutineContextElement(HeroChanges) {
    companion object Key : CoroutineContext.Key<HeroChanges>

    /** Ревизия инвентаря героя до первого сдвига в этом запросе, после последнего и сколько сдвигов было. */
    class Bump(val before: Long, val after: Long, val count: Int)

    private val changed = LinkedHashMap<String, CharacterEquipment>()
    private val removed = LinkedHashSet<String>()
    private val bumps = HashMap<String, Bump>()

    @Synchronized fun upsert(item: CharacterEquipment) { removed -= item._id; changed[item._id] = item }

    @Synchronized fun remove(item: CharacterEquipment) { changed -= item._id; removed += item._id }

    @Synchronized fun bumped(characterId: String, before: Long) {
        val known = bumps[characterId]
        bumps[characterId] = Bump(known?.before ?: before, before + 1, (known?.count ?: 0) + 1)
    }

    @Synchronized fun bumpOf(characterId: String): Bump? = bumps[characterId]

    /** Дельта героя, если весь журнал про него; иначе null - снимок отдаст список целиком. */
    @Synchronized fun deltaFor(characterId: String): InventoryDelta? {
        if (changed.values.any { it.characterId != characterId }) return null
        return InventoryDelta(changed.values.toList(), removed.toList())
    }
}

/** Что запрос сделал с инвентарём героя. */
class InventoryDelta(val changed: List<CharacterEquipment>, val removed: List<String>)

/** Журнал текущего запроса, если он есть. */
suspend fun heroChanges(): HeroChanges? = coroutineContext[HeroChanges]

/** Каждый вызов получает свой журнал: он нужен снимку в конце того же запроса. */
fun Application.installHeroChanges() {
    intercept(ApplicationCallPipeline.Call) { withContext(HeroChanges()) { proceed() } }
}
