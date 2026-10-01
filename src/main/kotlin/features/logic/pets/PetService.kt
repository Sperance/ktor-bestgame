package features.logic.pets

import base.exception.model.CharacterExceptions
import base.exception.model.CurrencyExceptions
import com.sperance.exileforge.rules.content.ContentIndex
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.Incubation
import com.sperance.exileforge.rules.content.Omen
import com.sperance.exileforge.rules.content.Pet
import com.sperance.exileforge.rules.content.PetKind
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.Menagerie
import com.sperance.exileforge.rules.roll.OrbApplier
import config.ContentStore
import features.data.hero.Hero
import features.data.hero.HeroRepository
import features.logic.hero.sheetOf
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Зверинец как его видит клиент: питомцы, активные боевой и помощник, потолок, инкубатор (1.67.0). */
@Serializable
data class PetState(
    val pets: List<Pet> = emptyList(), val combat: String = "", val helper: String = "", val cap: Int = 0,
    val incubator: IncubatorState = IncubatorState(),
) {
    companion object {
        /** Зверинец героя на часах [now]: открытые места инкубатора - по листу героя, занятые сверх них тоже видны. */
        fun of(hero: Hero, index: ContentIndex, now: Long = System.currentTimeMillis()): PetState {
            val open = Menagerie(index).incubatorSlots(index.sheetOf(hero).stats)
            val bySlot = hero.incubator.associateBy { it.slot }
            val shown = (0 until maxOf(open, (bySlot.keys.maxOrNull() ?: -1) + 1)).map { IncubatorSlot.of(it, it < open, bySlot[it], now) }
            return PetState(hero.pets.toList(), hero.petCombat, hero.petHelper, index.rules.pets.cap, IncubatorState(open, index.pets.incubator.maxSlots, shown, now))
        }
    }
}

/** Инкубатор героя: открытых мест [slots] из [max] и каждое место - пустое или с яйцом; [now] - часы сервера, мс эпохи. */
@Serializable
data class IncubatorState(val slots: Int = 0, val max: Int = 0, val entries: List<IncubatorSlot> = emptyList(), val now: Long = 0)

/**
 * Место инкубатора: [egg] пусто - место свободно; иначе решённые при закладке [rarity] и [level], срок [readyAt] (мс эпохи),
 * остаток [remainingSeconds] и готовность [ready]. [open] - false у занятого места сверх нынешнего числа открытых (снят ошейник).
 */
@Serializable
data class IncubatorSlot(
    val slot: Int, val open: Boolean = true, val egg: String = "", val rarity: Rarity? = null, val level: Int = 0,
    val startedAt: Long = 0, val readyAt: Long = 0, val remainingSeconds: Long = 0, val ready: Boolean = false,
) {
    companion object {
        fun of(slot: Int, open: Boolean, incubation: Incubation?, now: Long): IncubatorSlot = incubation?.let {
            IncubatorSlot(slot, open, it.egg, it.rarity, it.level, it.startedAt, it.readyAt, (it.remainingMillis(now) + 999) / 1000, it.ready(now))
        } ?: IncubatorSlot(slot, open)
    }
}

/**
 * Зверинец героя (1.5.0): яйцо из сумки ложится в инкубатор и вылупляется питомцем по сроку (1.67.0), сфера питомцев меняет
 * питомца, один боевой и один помощник в деле, лишнего можно отпустить за золото. Всё - одной записью документа героя.
 */
class PetService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val content: ContentStore by inject()
    private val index get() = content.index
    private val cap: Int get() = index.rules.pets.cap
    private val orbs by lazy { OrbApplier(index) }

    suspend fun state(heroId: String): PetState = PetState.of(heroes.requireHero(heroId, "pets"), index)

    /**
     * Закладка яйца [egg] в место [slot] инкубатора (1.67.0; null - первое свободное открытое). Яйцо списывается сразу; место
     * в зверинце держится за ним, чтобы вылупившемуся всегда было куда встать.
     */
    suspend fun incubate(heroId: String, egg: String, slot: Int? = null): PetState = command(heroId, "petIncubate") { hero, pets ->
        val method = "petIncubate"
        if (!pets.isEgg(egg)) throw CharacterExceptions.funExceptionNotPetItem(method, egg)
        if (hero.pets.size + hero.incubator.size >= cap) throw CharacterExceptions.funExceptionMenagerieFull(method, "$cap")
        val sheet = index.sheetOf(hero).stats
        val open = pets.incubatorSlots(sheet)
        val taken = hero.incubator.mapTo(HashSet()) { it.slot }
        val place = slot ?: (0 until open).firstOrNull { it !in taken } ?: throw CharacterExceptions.funExceptionIncubatorSlot(method, "")
        if (place !in 0 until open || place in taken) throw CharacterExceptions.funExceptionIncubatorSlot(method, "$place")
        hero.spend(egg, 1, method)
        hero.incubator += pets.incubate(egg, place, hero.level, sheet, System.currentTimeMillis(), Dice.system())
            ?: throw CharacterExceptions.funExceptionNotPetItem(method, egg)
    }

    /** Прежняя мгновенная команда: теперь закладывает яйцо в первое свободное место инкубатора. */
    suspend fun hatch(heroId: String, egg: String): PetState = incubate(heroId, egg)

    /** Забрать вылупившегося из места [slot]: готовность - по часам сервера, питомец встаёт в зверинец. */
    suspend fun collect(heroId: String, slot: Int): PetState = command(heroId, "petCollect") { hero, pets ->
        val method = "petCollect"
        val incubation = hero.incubator.firstOrNull { it.slot == slot } ?: throw CharacterExceptions.funExceptionIncubationNotReady(method, "$slot")
        if (!incubation.ready(System.currentTimeMillis())) throw CharacterExceptions.funExceptionIncubationNotReady(method, "$slot")
        if (hero.pets.size >= cap) throw CharacterExceptions.funExceptionMenagerieFull(method, "$cap")
        hero.pets += pets.hatch(incubation, Hero.newItemId(), Dice.system()) ?: throw CharacterExceptions.funExceptionNotPetItem(method, incubation.egg)
        hero.incubator.remove(incubation)
    }

    /**
     * Сфера на питомце (1.65.0): сфера ремесла вещей - через [OrbApplier] с целью-питомцем и знамением [omenCode], сфера роста - своя.
     * Сфера и знамение списываются той же записью; отказ правила их не съедает.
     */
    suspend fun orb(heroId: String, petId: String, orb: String, omenCode: String? = null): PetState = command(heroId, "petOrb") { hero, pets ->
        val method = "petOrb"
        val pet = requirePet(hero, petId, method)
        val crafting = index.orb(orb)
        if (crafting != null) {
            val omen = omenCode?.takeIf { it.isNotBlank() }?.let { Omen.of(it) ?: throw CurrencyExceptions.funExceptionNotCurrency(method, it) }
            val outcome = orbs.apply(crafting, pet, Dice.system(), omen)
            hero.spend(orb, 1, method)
            omen?.let { hero.spend(it.code, 1, method) }
            hero.count(Counter.ORBS_USED)
            hero.replacePet(outcome.pet)
            return@command
        }
        val action = pets.orb(orb) ?: throw CharacterExceptions.funExceptionNotPetItem(method, orb)
        val next = pets.apply(action, pet, Dice.system()) ?: throw CharacterExceptions.funExceptionPetOrbIdle(method, orb)
        hero.spend(orb, 1, method)
        hero.replacePet(next)
    }

    /** Выбор [choice] из вариантов знамения выбора на питомце (1.65.0): бесплатно. */
    suspend fun choose(heroId: String, petId: String, choice: Int): PetState = command(heroId, "petChoose") { hero, _ ->
        hero.replacePet(orbs.choose(requirePet(hero, petId, "petChoose"), choice).pet)
    }

    /** Питомец в дело на место своего рода; повторно - снять с места. */
    suspend fun activate(heroId: String, petId: String): PetState = command(heroId, "petActivate") { hero, pets ->
        val pet = requirePet(hero, petId, "petActivate")
        when (pets.species(pet.species)?.kind) {
            PetKind.COMBAT -> hero.petCombat = if (hero.petCombat == pet.id) "" else pet.id
            PetKind.HELPER -> hero.petHelper = if (hero.petHelper == pet.id) "" else pet.id
            null -> throw CharacterExceptions.funExceptionPetNotFound("petActivate", petId)
        }
    }

    /** Отпустить питомца за золото по его редкости и уровню. */
    suspend fun release(heroId: String, petId: String): PetState = command(heroId, "petRelease") { hero, _ ->
        val pet = requirePet(hero, petId, "petRelease")
        hero.pets.remove(pet)
        if (hero.petCombat == pet.id) hero.petCombat = ""
        if (hero.petHelper == pet.id) hero.petHelper = ""
        hero.gain(index.rules.pets.releasePrice(pet.rarity, pet.level))
    }

    private fun requirePet(hero: Hero, id: String, method: String): Pet = hero.pet(id) ?: throw CharacterExceptions.funExceptionPetNotFound(method, id)

    private suspend fun command(heroId: String, method: String, block: (Hero, Menagerie) -> Unit): PetState {
        val hero = heroes.requireHero(heroId, method)
        val pets = Menagerie(index)
        block(hero, pets)
        heroes.save(hero, method)
        return PetState.of(hero, index)
    }
}
