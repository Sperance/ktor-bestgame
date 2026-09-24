package features.logic.campaign

import features.logic.pools.Pools
import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import application.enums.EnumStatStock
import base.exception.model.CampaignExceptions
import base.exception.model.CharacterExceptions
import config.MongoFactory.transactionExecute
import features.caches.EquipmentCache
import features.caches.ExperienceLevelCache
import features.caches.ItemsCache
import features.caches.ModifierDefinitionCache
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
data class ChestState(val left: Int, val refreshAt: Long, val bought: Boolean = false)

/** Что стало после услуги карты (0.34.0): золото героя, сундуки и босс карты. */
@Serializable
data class MapServiceOutcome(val money: Long, val chests: ChestState, val boss: BossState)

/** Босс карты у героя (с 0.32.0): жив ли он и когда вернётся убитый (миллисекунды эпохи). */
@Serializable
data class BossState(val alive: Boolean, val respawnAt: Long)

/**
 * Вход в локацию (с 0.35.0): карта, с которой герой вошёл, - её сложенные модификаторы и бонус к
 * добыче, - или null без карты, и сундуки локации с учётом тех, что карта добавила.
 */
@Serializable
data class MapLaunch(val map: ActiveMap?, val chests: ChestState)

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
    private companion object {
        /** Обычная уникалка с босса - не старше уровня карты больше чем на столько. */
        const val UNIQUE_REACH = 10
    }

    private val characters: CharacterRepository by inject()
    private val inventory: CharacterEquipmentRepository by inject()
    private val equipmentCache: EquipmentCache by inject()
    private val itemsCache: ItemsCache by inject()
    private val levels: ExperienceLevelCache by inject()
    private val definitions: ModifierDefinitionCache by inject()

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
        // Уникальная редкость - только у босса, а босс сообщается своим маршрутом.
        val rarityValue = EnumMonsterRarity.entries.firstOrNull { it.name == rarityName && it != EnumMonsterRarity.UNIQUE }
            ?: throw CampaignExceptions.funExceptionRarity(method, rarityName)
        val rarity = CampaignContent.file.rarities.first { it.rarity == rarityValue }
        val monster = CampaignContent.monsters.getValue(monsterCode)

        val experience = CampaignLoot.experience(monster, map.level, rarity, bonus(characterId, EnumStatStock.STOCK_EXPERIENCE) + mapBonus(character, mapCode).experience)
        return grant(character, CampaignContent.file.lootTables.getValue(monster.loot), map.level, rarity, experience, method,
            mapCode = mapCode, mapChance = CampaignContent.file.maps.dropChance * rarity.quantity)
    }

    /** Жив ли босс карты у героя и когда вернётся убитый (с 0.32.0). */
    suspend fun boss(characterId: String, mapCode: String): BossState {
        val method = "boss"
        val character = requireCharacter(characterId, method)
        openMap(character, mapCode, method)
        val back = character.bosses[mapCode] ?: 0L
        return BossState(System.currentTimeMillis() >= back, back)
    }

    /**
     * Герой убил босса карты: добыча его таблицы с уникальной редкостью, шанс обычной уникалки и
     * шанс его собственной, и выход открыт на [BossRule.respawnHours] часов. Мёртвого не убить - `CP_008`.
     */
    suspend fun slayBoss(characterId: String, mapCode: String): CampaignReward {
        val method = "slayBoss"
        val character = requireCharacter(characterId, method)
        val map = openMap(character, mapCode, method)
        val now = System.currentTimeMillis()
        if (now < (character.bosses[mapCode] ?: 0L)) throw CampaignExceptions.funExceptionBossSlain(method, mapCode)
        val template = CampaignContent.monsters.getValue(map.boss.code)
        val rule = CampaignContent.file.bosses
        val rarity = CampaignContent.file.rarities.first { it.rarity == EnumMonsterRarity.UNIQUE }
        val random = Random.Default
        val all = equipmentCache.getCache()
        val ordinary = Pools.of(all, rule.uniquePools)
        val extra = listOfNotNull(
            Pools.draw(ordinary.filter { it.value.requiredLevel <= map.level + UNIQUE_REACH }.ifEmpty { ordinary }, random).takeIf { random.nextDouble() < rule.uniqueChance },
            Pools.draw(Pools.of(all, template.uniquePools), random).takeIf { random.nextDouble() < rule.ownUniqueChance },
        )
        character.bosses[mapCode] = now + (rule.respawnHours * 3_600_000).toLong()
        val sheet = characters.calculateStats(characterId).stats
        val experience = CampaignLoot.experience(template, map.level, rarity, (sheet[EnumStatStock.STOCK_EXPERIENCE] ?: 0.0) + mapBonus(character, mapCode).experience)
        return grant(character, CampaignContent.file.lootTables.getValue(template.loot), map.level, rarity, experience, method, extra,
            mapCode = mapCode, mapChance = CampaignContent.file.maps.bossChance)
    }

    /**
     * Вход в локацию (с 0.35.0). С картой [itemId] она тратится: её уровень должен совпасть с
     * локацией (`CP_011`), модификаторы складываются в [ActiveMap] и ложатся на добычу этой локации до
     * следующего входа, а `MAP_CHESTS` сразу добавляет сундуки в окно. Без карты прежний бонус снимается.
     */
    suspend fun start(characterId: String, mapCode: String, itemId: String?): MapLaunch {
        val method = "start"
        val character = requireCharacter(characterId, method)
        openMap(character, mapCode, method)
        val window = windowOf(character, mapCode, characterId, method)
        val item = itemId?.let { id -> inventory.findById(id)?.takeIf { it.characterId == characterId } ?: throw CharacterExceptions.funExceptionItemNotFound(method, id) }
        val template = item?.let { equipmentCache.findById(it.equipmentId) }
        if (item != null && (template == null || template.slot != EnumEquipmentType.MAP || template.code != CampaignMaps.templateCode(mapCode) || item.equippedSlot != null))
            throw CampaignExceptions.funExceptionMapItem(method, template?.code ?: item.equipmentId)
        val active = item?.let { map ->
            val effects = mutableMapOf<String, Double>()
            map.params.forEach { modifier ->
                definitions.findById(modifier.modifierId)?.effects?.forEachIndexed { index, effect ->
                    effects.merge((effect.stat as Enum<*>).name, modifier.values.getOrElse(index) { 0.0 }, Double::plus)
                }
            }
            CampaignMaps.active(CampaignContent.file.maps, mapCode, effects, map.rarity)
        }
        val chests = active?.effects?.get(CampaignMaps.CHESTS)?.toInt() ?: 0
        if (chests > 0) character.chests[mapCode] = window.copy(left = window.left + chests)
        character.activeMap = active
        transactionExecute(method) { session ->
            item?.let { inventory.deleteById(it._id, session) }
            characters.update(character, session)
        }
        val now = character.chests[mapCode] ?: window
        return MapLaunch(active, ChestState(now.left, now.refreshAt, now.bought))
    }

    /** Бонус карты, с которой герой вошёл в [mapCode]; в другой локации его нет. */
    private fun mapBonus(character: Character, mapCode: String): ActiveMap =
        character.activeMap?.takeIf { it.mapCode == mapCode } ?: ActiveMap(mapCode)

    /** Сколько сундуков ещё стоит на карте у героя и когда их станет снова (с 0.31.0). */
    suspend fun chests(characterId: String, mapCode: String): ChestState {
        val method = "chests"
        val character = requireCharacter(characterId, method)
        openMap(character, mapCode, method)
        val window = windowOf(character, mapCode, characterId, method)
        return ChestState(window.left, window.refreshAt, window.bought)
    }

    /** «Карта сокровищ» (0.34.0): ещё один сундук в текущем окне карты, раз за окно, за золото. */
    suspend fun treasure(characterId: String, mapCode: String): MapServiceOutcome {
        val method = "treasure"
        val character = requireCharacter(characterId, method)
        val map = openMap(character, mapCode, method)
        val window = windowOf(character, mapCode, characterId, method)
        if (window.bought) throw CampaignExceptions.funExceptionTreasureBought(method, mapCode)
        charge(character, CampaignContent.file.services.treasurePerLevel * map.level, method)
        character.chests[mapCode] = window.copy(left = window.left + 1, bought = true)
        transactionExecute(method) { session -> characters.update(character, session) }
        return outcome(character, mapCode)
    }

    /** «Вызов стража» (0.34.0): убитый босс карты снова стоит у выхода, за золото. */
    suspend fun summon(characterId: String, mapCode: String): MapServiceOutcome {
        val method = "summon"
        val character = requireCharacter(characterId, method)
        val map = openMap(character, mapCode, method)
        if (System.currentTimeMillis() >= (character.bosses[mapCode] ?: 0L)) throw CampaignExceptions.funExceptionBossStands(method, mapCode)
        charge(character, CampaignContent.file.services.summonPerLevel * map.level, method)
        character.bosses.remove(mapCode)
        transactionExecute(method) { session -> characters.update(character, session) }
        return outcome(character, mapCode)
    }

    private fun charge(character: Character, price: Long, method: String) {
        if (character.money < price) throw base.exception.model.CharacterExceptions.funExceptionGold(method, price.toString())
        character.money -= price
    }

    private fun outcome(character: Character, mapCode: String): MapServiceOutcome {
        val window = character.chests[mapCode] ?: ChestWindow()
        val back = character.bosses[mapCode] ?: 0L
        return MapServiceOutcome(character.money, ChestState(window.left, window.refreshAt, window.bought), BossState(System.currentTimeMillis() >= back, back))
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
            CampaignChests.rarity(CampaignContent.file.chests), 0.0, method, mapCode = mapCode)
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

    /**
     * Добыча по таблице и опыт - одной транзакцией, как у промокода. Карта, с которой герой вошёл
     * в [mapCode], добавляет свои количество и редкость; с шансом [mapChance] (с 0.35.0) падает карта.
     */
    private suspend fun grant(character: Character, table: LootTable, level: Int, rarity: CampaignRarity, experience: Double, method: String,
                              extra: List<features.data.equipment.equipment_data.Equipment> = emptyList(),
                              mapCode: String, mapChance: Double = 0.0): CampaignReward {
        val sheet = characters.calculateStats(character._id).stats
        val active = mapBonus(character, mapCode)
        fun bonus(stat: EnumStatStock) = sheet[stat] ?: 0.0
        val random = Random.Default
        val quantity = bonus(EnumStatStock.STOCK_QUANTITY) + active.quantity
        val loot = CampaignLoot.roll(table, level, rarity, quantity, bonus(EnumStatStock.STOCK_GOLD), random)
        val orbs = loot.orbs.mapNotNull { (code, amount) ->
            itemsCache.getCache().firstOrNull { it.code == code }?.let { CharacterItems(it._id, amount) }
        }
        // Экипировка тянется из пулов строки таблицы: что в них не состоит, отсюда не падает.
        val wearable = equipmentCache.getCache().filter { it.requiredLevel <= level }
        val templates = extra + loot.equipment.mapNotNull { pools ->
            CampaignLoot.pick(Pools.of(wearable, pools), { it.rarity }, rarity.rarityBonus + bonus(EnumStatStock.STOCK_RARITY) + active.rarity, random)
        }
        val rule = CampaignContent.file.maps
        val dropped = CampaignMaps.drop(rule, mapChance * (1 + quantity / 100), mapCode, CampaignContent.maps.keys.toList(), random)
            ?.let { code -> equipmentCache.getCache().firstOrNull { it.code == CampaignMaps.templateCode(code) } }

        val equipment = transactionExecute(method) { session ->
            if (orbs.isNotEmpty()) characters.applyItems(character, orbs, method)
            if (experience > 0) characters.applyExperience(character, experience, method)
            character.money += loot.gold
            val created = templates.map { inventory.addFromEquipment(character._id, it, session) } +
                listOfNotNull(dropped?.let { inventory.addRolled(character._id, it, CampaignMaps.rarity(rule, random), session) })
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
        // Смерть тратит и карту: её бонус до следующего входа пропадает, как в PoE.
        val spent = character.activeMap?.mapCode == mapCode
        if (spent) character.activeMap = null
        if (lost > 0) character.experience -= lost
        if (lost > 0 || spent) transactionExecute(method) { session -> characters.update(character, session) }
        return CampaignFall(lost, character.level.toInt(), character.experience)
    }

    /**
     * Герой дошёл до выхода: карта пройдена и открывает следующую. Повторное прохождение ничего не меняет.
     * С 0.32.0 выход запечатан, пока жив босс карты (`CP_007`).
     */
    suspend fun complete(characterId: String, mapCode: String): CampaignProgress {
        val method = "complete"
        val character = requireCharacter(characterId, method)
        openMap(character, mapCode, method)
        if (System.currentTimeMillis() >= (character.bosses[mapCode] ?: 0L)) throw CampaignExceptions.funExceptionSealed(method, mapCode)
        val spent = character.activeMap?.mapCode == mapCode
        val fresh = mapCode !in character.campaign
        if (spent) character.activeMap = null
        if (fresh) character.campaign.add(mapCode)
        if (spent || fresh) transactionExecute(method) { session -> characters.update(character, session) }
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
