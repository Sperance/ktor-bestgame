package features.logic.campaign

import application.enums.EnumStatStock
import base.exception.model.CampaignExceptions
import base.exception.model.CharacterExceptions
import config.MongoFactory.transactionExecute
import features.caches.EquipmentCache
import features.caches.ExperienceLevelCache
import features.caches.ItemsCache
import features.data.character.Character
import features.data.character.CharacterRepository
import features.data.character.character_data.CharacterItems
import features.data.inventory.CharacterEquipment
import features.data.inventory.CharacterEquipmentRepository
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.random.Random

/** Какие карты персонаж прошёл и какие ему открыты. */
@Serializable
data class CampaignProgress(val cleared: List<String>, val unlocked: List<String>)

/**
 * Что принесло одно убийство - и где теперь персонаж.
 *
 * Экземпляры экипировки отдаются целиком: клиент показывает их в списке добычи тем же
 * `ItemRow`, что и тайник, не перечитывая инвентарь.
 */
@Serializable
data class CampaignReward(
    val experience: Double,
    val gold: Long,
    val items: List<CharacterItems>,
    val equipment: List<CharacterEquipment>,
    val level: Int,
    val totalExperience: Double,
    val money: Long,
)

/** Сундуки карты у героя (с 0.31.0): сколько ещё можно открыть и когда окно бросится заново (миллисекунды эпохи). */
@Serializable
data class ChestState(val left: Int, val refreshAt: Long)

/** Что стоила смерть: потерянный опыт и где герой теперь. Уровень не меняется никогда. */
@Serializable
data class CampaignFall(val lost: Double, val level: Int, val totalExperience: Double)

/**
 * Кампания персонажа: прогресс по картам, награда за убийство, смерть и прохождение карты.
 *
 * С 0.26.0 бой считает клиент и присылает итог - так решил владелец проекта. Сервер проверяет
 * то, что может проверить без боя: карта существует и открыта, монстр на ней водится, редкость
 * известна. Добычу и опыт он катает сам по своим таблицам, поэтому клиент не может выписать себе
 * ни сферу, ни уровень, - только убить монстра, которого не убивал.
 */
class CampaignService : KoinComponent {
    private val characters: CharacterRepository by inject()
    private val inventory: CharacterEquipmentRepository by inject()
    private val equipmentCache: EquipmentCache by inject()
    private val itemsCache: ItemsCache by inject()
    private val levels: ExperienceLevelCache by inject()

    fun view(): CampaignView = CampaignContent.view

    suspend fun progress(characterId: String): CampaignProgress = progressOf(requireCharacter(characterId, "progress"))

    /**
     * Награда за монстра: опыт, золото, сферы и экипировка - одной транзакцией, как у промокода.
     */
    suspend fun kill(characterId: String, mapCode: String, monsterCode: String, rarityName: String): CampaignReward {
        val method = "kill"
        val character = requireCharacter(characterId, method)
        val map = openMap(character, mapCode, method)
        if (map.monsters.none { it.code == monsterCode }) throw CampaignExceptions.funExceptionMonsterNotOnMap(method, monsterCode)
        val rarityValue = EnumMonsterRarity.entries.firstOrNull { it.name == rarityName }
            ?: throw CampaignExceptions.funExceptionRarity(method, rarityName)
        val rarity = CampaignContent.file.rarities.first { it.rarity == rarityValue }
        val monster = CampaignContent.monsters.getValue(monsterCode)

        val experience = CampaignLoot.experience(monster, map.level, rarity, bonus(characterId, EnumStatStock.STOCK_EXPERIENCE))
        return grant(character, CampaignContent.file.lootTables.getValue(monster.loot), map.level, rarity, experience, method)
    }

    /** Сколько сундуков ещё стоит на карте у героя и когда их станет снова (с 0.31.0). */
    suspend fun chests(characterId: String, mapCode: String): ChestState {
        val method = "chests"
        val character = requireCharacter(characterId, method)
        openMap(character, mapCode, method)
        val window = windowOf(character, mapCode, characterId, method)
        return ChestState(window.left, window.refreshAt)
    }

    /**
     * Герой открыл сундук: из окна карты уходит один, добыча катается по таблице сундуков карты.
     * Пустое окно - отказ `CP_006`: клиент не может открыть сундуков больше, чем сервер поставил.
     */
    suspend fun openChest(characterId: String, mapCode: String): CampaignReward {
        val method = "openChest"
        val character = requireCharacter(characterId, method)
        val map = openMap(character, mapCode, method)
        val window = windowOf(character, mapCode, characterId, method)
        if (window.left <= 0) throw CampaignExceptions.funExceptionNoChest(method, mapCode)
        character.chests[mapCode] = window.copy(left = window.left - 1)
        val template = CampaignContent.file.chapters.flatMap { it.maps }.first { it.code == mapCode }
        return grant(character, CampaignContent.file.lootTables.getValue(template.chestLoot), map.level,
            CampaignChests.rarity(CampaignContent.file.chests), 0.0, method)
    }

    /** Окно сундуков карты; истёкшее бросается заново и сохраняется сразу. */
    private suspend fun windowOf(character: Character, mapCode: String, characterId: String, method: String): ChestWindow {
        val current = character.chests[mapCode]
        val window = CampaignChests.window(current, System.currentTimeMillis(), CampaignContent.file.chests,
            bonus(characterId, EnumStatStock.STOCK_CHEST_QUANTITY), Random.Default)
        if (window != current) {
            character.chests[mapCode] = window
            transactionExecute(method) { session -> characters.update(character, session) }
        }
        return window
    }

    /** Бонус героя из того же листа, что и всё остальное: работают только надетые вещи. */
    private suspend fun bonus(characterId: String, stat: EnumStatStock): Double =
        characters.calculateStats(characterId).stats[stat] ?: 0.0

    /** Добыча по таблице и опыт - одной транзакцией, как у промокода. */
    private suspend fun grant(character: Character, table: LootTable, level: Int, rarity: CampaignRarity, experience: Double, method: String): CampaignReward {
        val sheet = characters.calculateStats(character._id).stats
        fun bonus(stat: EnumStatStock) = sheet[stat] ?: 0.0
        val random = Random.Default
        val loot = CampaignLoot.roll(table, level, rarity, bonus(EnumStatStock.STOCK_QUANTITY), bonus(EnumStatStock.STOCK_GOLD), random)
        val orbs = loot.orbs.mapNotNull { (code, amount) ->
            itemsCache.getCache().firstOrNull { it.code == code }?.let { CharacterItems(it._id, amount) }
        }
        val bases = equipmentCache.getCache().filter { it.requiredLevel <= level }
        val templates = List(loot.equipment) {
            CampaignLoot.pick(bases, { it.rarity }, rarity.rarityBonus + bonus(EnumStatStock.STOCK_RARITY), random)
        }.filterNotNull()

        val equipment = transactionExecute(method) { session ->
            if (orbs.isNotEmpty()) characters.applyItems(character, orbs, method)
            if (experience > 0) characters.applyExperience(character, experience, method)
            character.money += loot.gold
            val created = templates.map { inventory.addFromEquipment(character._id, it, session) }
            characters.update(character, session)
            created
        }
        return CampaignReward(experience, loot.gold, orbs, equipment, character.level.toInt(), character.experience, character.money)
    }

    /**
     * Герой погиб на карте: часть опыта текущего уровня теряется по правилу [CombatRules.death].
     * Карта должна быть открыта, как и для убийства; на ранних картах правило ничего не отнимает.
     */
    suspend fun fall(characterId: String, mapCode: String): CampaignFall {
        val method = "fall"
        val character = requireCharacter(characterId, method)
        val map = openMap(character, mapCode, method)
        val floor = levels.ordered().lastOrNull { it.level <= character.level.toInt() }?.experience ?: 0.0
        val lost = CampaignDeath.lost(CampaignContent.file.combat.death, map.level, character.experience, floor, levels.nextLevelExperience(character.level.toInt()))
        if (lost > 0) {
            character.experience -= lost
            transactionExecute(method) { session -> characters.update(character, session) }
        }
        return CampaignFall(lost, character.level.toInt(), character.experience)
    }

    /** Герой дошёл до выхода: карта пройдена и открывает следующую. Повторное прохождение ничего не меняет. */
    suspend fun complete(characterId: String, mapCode: String): CampaignProgress {
        val method = "complete"
        val character = requireCharacter(characterId, method)
        openMap(character, mapCode, method)
        if (mapCode !in character.campaign) {
            character.campaign.add(mapCode)
            transactionExecute(method) { session -> characters.update(character, session) }
        }
        return progressOf(character)
    }

    private fun openMap(character: Character, mapCode: String, method: String): CampaignMap {
        val map = CampaignContent.maps[mapCode] ?: throw CampaignExceptions.funExceptionMapNotFound(method, mapCode)
        if (mapCode !in CampaignContent.unlocked(character.campaign)) throw CampaignExceptions.funExceptionMapLocked(method, mapCode)
        return map
    }

    private fun progressOf(character: Character) =
        CampaignProgress(character.campaign.filter { it in CampaignContent.maps }, CampaignContent.unlocked(character.campaign))

    private suspend fun requireCharacter(characterId: String, method: String): Character =
        characters.findById(characterId) ?: throw CharacterExceptions.funExceptionNotFound(method, characterId)
}
