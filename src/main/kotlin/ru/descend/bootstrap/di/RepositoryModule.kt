package ru.descend.bootstrap.di

import org.koin.dsl.module
import ru.descend.features.blocklist.persistence.BlockListRepository
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.features.items.persistence.ItemsRepository
import ru.descend.features.modifiers.persistence.ModifierDefinitionRepository
import ru.descend.features.recipe.persistence.RecipeRepository
import ru.descend.features.redemptioncodes.persistence.RedemptionCodesRepository
import ru.descend.features.user.persistence.UserRepository

val repositoryModule = module {
    single { ru.descend.features.passives.persistence.PassiveTreeRepository() }
    single { ru.descend.features.passives.persistence.PassiveReceiptRepository() }
    single { ru.descend.features.passives.application.PassiveService(get(), get(), get(), get()) }
    single { ru.descend.features.combat.persistence.CombatWorldRepository() }
    single { ru.descend.features.combat.persistence.BattleReceiptRepository() }
    single { ru.descend.features.combat.application.CombatService(get(), get(), get(), get(), get(), get(), get()) }
    single { ru.descend.features.character.application.EquipmentService(get(), get(), get(), get(), get(), get()) }
    single { ru.descend.features.character.application.InventoryCommandService(get(), get(), get(), get(), get()) }
    single { UserRepository() }
    single { CharacterRepository() }
    single { ru.descend.features.character.persistence.CharacterEquipmentRepository() }
    single { ru.descend.features.character.persistence.CharacterInventoryRepository() }
    single { ItemsRepository() }
    single { EquipmentRepository() }
    single { BlockListRepository() }
    single { RecipeRepository() }
    single { RedemptionCodesRepository() }
    single { ModifierDefinitionRepository() }
    single { ru.descend.features.poe.persistence.MongoModifierCatalog(get()) }
    single { ru.descend.features.poe.persistence.ReceiptRepository() }
    single { ru.descend.features.poe.application.PoeService(get(), get(), get(), get(), get(), get(), get()) }
}

