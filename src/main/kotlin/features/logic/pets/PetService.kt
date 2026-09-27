package features.logic.pets

import base.exception.model.CharacterExceptions
import com.sperance.exileforge.rules.content.Pet
import com.sperance.exileforge.rules.content.PetKind
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.Menagerie
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

    suspend fun state(heroId: String): PetState = PetState.of(heroes.requireHero(heroId, "pets"), cap)

    suspend fun hatch(heroId: String, egg: String): PetState = command(heroId, "petHatch") { hero, pets ->
        if (!pets.isEgg(egg)) throw CharacterExceptions.funExceptionNotPetItem("petHatch", egg)
        if (hero.pets.size >= cap) throw CharacterExceptions.funExceptionMenagerieFull("petHatch", "$cap")
        hero.spend(egg, 1, "petHatch")
        hero.pets += pets.hatch(egg, Hero.newItemId(), Dice.system()) ?: throw CharacterExceptions.funExceptionNotPetItem("petHatch", egg)
    }

    suspend fun orb(heroId: String, petId: String, orb: String): PetState = command(heroId, "petOrb") { hero, pets ->
        val action = pets.orb(orb) ?: throw CharacterExceptions.funExceptionNotPetItem("petOrb", orb)
        val pet = requirePet(hero, petId, "petOrb")
        val next = pets.apply(action, pet, Dice.system()) ?: throw CharacterExceptions.funExceptionPetOrbIdle("petOrb", orb)
        hero.spend(orb, 1, "petOrb")
        hero.replacePet(next)
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
