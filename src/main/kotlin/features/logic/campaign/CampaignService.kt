package features.logic.campaign

import application.enums.EnumStatStock
import base.exception.model.CampaignExceptions
import base.exception.model.CharacterExceptions
import config.MongoFactory.transactionExecute
import features.caches.EquipmentCache
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

/**
 * Кампания персонажа: прогресс по картам, награда за убийство и прохождение карты.
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

        // Бонусы героя к добыче - из того же листа, что и всё остальное: работают только надетые вещи.
        val sheet = characters.calculateStats(characterId).stats
        fun bonus(stat: EnumStatStock) = sheet[stat] ?: 0.0

        val random = Random.Default
        val experience = CampaignLoot.experience(monster, map.level, rarity, bonus(EnumStatStock.STOCK_EXPERIENCE))
        val loot = CampaignLoot.roll(CampaignContent.file.lootTables.getValue(monster.loot), map.level, rarity,
            bonus(EnumStatStock.STOCK_QUANTITY), bonus(EnumStatStock.STOCK_GOLD), random)
        val orbs = loot.orbs.mapNotNull { (code, amount) ->
            itemsCache.getCache().firstOrNull { it.code == code }?.let { CharacterItems(it._id, amount) }
        }
        val bases = equipmentCache.getCache().filter { it.requiredLevel <= map.level }
        val templates = List(loot.equipment) {
            CampaignLoot.pick(bases, { it.rarity }, rarity.rarityBonus + bonus(EnumStatStock.STOCK_RARITY), random)
        }.filterNotNull()

        val equipment = transactionExecute(method) { session ->
            if (orbs.isNotEmpty()) characters.applyItems(character, orbs, method)
            characters.applyExperience(character, experience, method)
            character.money += loot.gold
            val created = templates.map { inventory.addFromEquipment(character._id, it, session) }
            characters.update(character, session)
            created
        }
        return CampaignReward(experience, loot.gold, orbs, equipment, character.level.toInt(), character.experience, character.money)
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
