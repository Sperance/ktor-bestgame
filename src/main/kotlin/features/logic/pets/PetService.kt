package features.logic.pets

import base.exception.model.CharacterExceptions
import base.exception.model.CurrencyExceptions
import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.Omen
import com.sperance.exileforge.rules.content.Pet
import com.sperance.exileforge.rules.content.PetKind
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.Menagerie
import com.sperance.exileforge.rules.roll.OrbApplier
import config.ContentStore
import features.data.hero.Hero
import features.data.hero.HeroRepository
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** Зверинец как его видит клиент: питомцы, активные боевой и помощник, потолок. */
@Serializable
data class PetState(val pets: List<Pet> = emptyList(), val combat: String = "", val helper: String = "", val cap: Int = 0) {
    companion object {
        fun of(hero: Hero, cap: Int) = PetState(hero.pets.toList(), hero.petCombat, hero.petHelper, cap)
    }
}

/**
 * Зверинец героя (1.5.0): яйцо из сумки вылупляется питомцем, сфера питомцев меняет питомца, один боевой
 * и один помощник в деле, лишнего можно отпустить за золото. Всё - одной записью документа героя.
 */
class PetService : KoinComponent {
    private val heroes: HeroRepository by inject()
    private val content: ContentStore by inject()
    private val index get() = content.index
    private val cap: Int get() = index.rules.pets.cap
    private val orbs by lazy { OrbApplier(index) }

    suspend fun state(heroId: String): PetState = PetState.of(heroes.requireHero(heroId, "pets"), cap)

    suspend fun hatch(heroId: String, egg: String): PetState = command(heroId, "petHatch") { hero, pets ->
        if (!pets.isEgg(egg)) throw CharacterExceptions.funExceptionNotPetItem("petHatch", egg)
        if (hero.pets.size >= cap) throw CharacterExceptions.funExceptionMenagerieFull("petHatch", "$cap")
        hero.spend(egg, 1, "petHatch")
        hero.pets += pets.hatch(egg, Hero.newItemId(), Dice.system()) ?: throw CharacterExceptions.funExceptionNotPetItem("petHatch", egg)
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
            val omen = omenCode?.let { Omen.of(it) ?: throw CurrencyExceptions.funExceptionNotCurrency(method, it) }
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
        block(hero, Menagerie(index))
        heroes.save(hero, method)
        return PetState.of(hero, cap)
    }
}
