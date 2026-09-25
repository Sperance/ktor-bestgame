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
    /** Рецепт верстака, только что найденный на карте (с 0.46.0); null почти всегда. */
    val recipeFound: features.logic.bench.BenchRecipe? = null,
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

        /** Шанс найти рецепт верстака с редкого монстра или босса, пока на этом заходе карты ещё не находили. */
        const val RECIPE_CHANCE = 0.10
    }

    private val characters: CharacterRepository by inject()
    private val inventory: CharacterEquipmentRepository by inject()
    private val equipmentCache: EquipmentCache by inject()
    private val itemsCache: ItemsCache by inject()
    private val levels: ExperienceLevelCache by inject()
    private val definitions: ModifierDefinitionCache by inject()

    fun view(): CampaignView = CampaignContent.view

    suspend fun progress(characterId: String): CampaignProgress = progressOf(characters.requireCharacter(characterId, "progress"))

    /**
     * Награда за монстра: опыт, золото, сферы и экипировка - одной транзакцией, как у промокода.
     */
    suspend fun kill(characterId: String, mapCode: String, monsterCode: String, rarityName: String): CampaignReward {
        val method = "kill"
        val character = characters.requireCharacter(characterId, method)
        val map = openMap(character, mapCode, method)
        if (map.monsters.none { it.code == monsterCode }) throw CampaignExceptions.funExceptionMonsterNotOnMap(method, monsterCode)
        // Уникальная редкость - только у босса, а босс сообщается своим маршрутом.
        val rarityValue = EnumMonsterRarity.entries.firstOrNull { it.name == rarityName && it != EnumMonsterRarity.UNIQUE }
            ?: throw CampaignExceptions.funExceptionRarity(method, rarityName)
        val rarity = CampaignContent.file.rarities.first { it.rarity == rarityValue }
        val monster = CampaignContent.monsters.getValue(monsterCode)

        val sheet = characters.calculateStats(character).stats
        val experience = CampaignLoot.experience(monster, map.level, rarity, (sheet[EnumStatStock.STOCK_EXPERIENCE] ?: 0.0) + mapBonus(character, mapCode).experience)
        val recipe = if (rarityValue == EnumMonsterRarity.RARE) rollRecipe(character, map.level, Random.Default) else null
        return grant(character, sheet, CampaignContent.file.lootTables.getValue(monster.loot), map.level, rarity, experience, method,
            mapCode = mapCode, mapChance = CampaignContent.file.maps.dropChance * rarity.quantity, recipeFound = recipe)
    }

    /** Жив ли босс карты у героя и когда вернётся убитый (с 0.32.0). */
    suspend fun boss(characterId: String, mapCode: String): BossState {
        val method = "boss"
        val character = characters.requireCharacter(characterId, method)
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
        val character = characters.requireCharacter(characterId, method)
        val map = openMap(character, mapCode, method)
        val now = System.currentTimeMillis()
        if (now < (character.bosses[mapCode] ?: 0L)) throw CampaignExceptions.funExceptionBossSlain(method, mapCode)
        val template = CampaignContent.monsters.getValue(map.boss.code)
        val rule = CampaignContent.file.bosses
        val rarity = CampaignContent.file.rarities.first { it.rarity == EnumMonsterRarity.UNIQUE }
        val random = Random.Default
        val extra = listOfNotNull(
            Pools.draw(equipmentCache.poolUpTo(rule.uniquePools, map.level + UNIQUE_REACH).ifEmpty { equipmentCache.pool(rule.uniquePools) }, random).takeIf { random.nextDouble() < rule.uniqueChance },
            Pools.draw(equipmentCache.pool(template.uniquePools), random).takeIf { random.nextDouble() < rule.ownUniqueChance },
        )
        val sheet = characters.calculateStats(character).stats
        character.bosses[mapCode] = now + (rule.respawnHours * 3_600_000).toLong()
        val experience = CampaignLoot.experience(template, map.level, rarity, (sheet[EnumStatStock.STOCK_EXPERIENCE] ?: 0.0) + mapBonus(character, mapCode).experience)
        val recipe = rollRecipe(character, map.level, random)
        return grant(character, sheet, CampaignContent.file.lootTables.getValue(template.loot), map.level, rarity, experience, method, extra,
            mapCode = mapCode, mapChance = CampaignContent.file.maps.bossChance, recipeFound = recipe)
    }

    /**
     * Герой одолел стража осквернённой зоны (с 0.46.0): не больше одного раза за заход карты
     * (`CP_012`), и не таблица монстра, а его собственная, с шансом на уникалку из
     * [CorruptionRule.uniquePools]. Зона не персистентна - `start()` сбрасывает счётчик.
     */
    suspend fun corrupt(characterId: String, mapCode: String, monsterCode: String): CampaignReward {
        val method = "corrupt"
        val character = characters.requireCharacter(characterId, method)
        val map = openMap(character, mapCode, method)
        if (map.corrupted.code != monsterCode) throw CampaignExceptions.funExceptionMonsterNotOnMap(method, monsterCode)
        if (character.corruptionOpened) throw CampaignExceptions.funExceptionCorruptionSpent(method, mapCode)
        character.corruptionOpened = true
        val template = CampaignContent.monsters.getValue(map.corrupted.code)
        val rule = CampaignContent.file.corruption
        val rarity = CampaignContent.file.rarities.first { it.rarity == EnumMonsterRarity.UNIQUE }
        val random = Random.Default
        val extra = listOfNotNull(
            Pools.draw(equipmentCache.poolUpTo(rule.uniquePools, map.level + UNIQUE_REACH).ifEmpty { equipmentCache.pool(rule.uniquePools) }, random).takeIf { random.nextDouble() < rule.uniqueChance },
        )
        val sheet = characters.calculateStats(character).stats
        val experience = CampaignLoot.experience(template, map.level, rarity, (sheet[EnumStatStock.STOCK_EXPERIENCE] ?: 0.0) + mapBonus(character, mapCode).experience)
        return grant(character, sheet, CampaignContent.file.lootTables.getValue(template.loot), map.level, rarity, experience, method, extra, mapCode = mapCode)
    }

    /**
     * Вход в локацию (с 0.35.0). С картой [itemId] она тратится: её уровень должен совпасть с
     * локацией (`CP_011`), модификаторы складываются в [ActiveMap] и ложатся на добычу этой локации до
     * следующего входа, а `MAP_CHESTS` сразу добавляет сундуки в окно. Без карты прежний бонус снимается.
     */
    suspend fun start(characterId: String, mapCode: String, itemId: String?): MapLaunch {
        val method = "start"
        val character = characters.requireCharacter(characterId, method)
        openMap(character, mapCode, method)
        val window = windowOf(character, mapCode, characterId, method)
        val item = itemId?.let { inventory.requireOwned(characterId, it, method) }
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
        character.mapRecipeRolled = false
        character.corruptionOpened = false
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

    /**
     * Рецепт верстака с редкого монстра или босса (с 0.46.0): не больше одного за заход карты,
     * тир решает [features.logic.bench.CraftingBench.tierFor] уровня локации. Мутирует [character]
     * на месте - сохраняет его вызывающая транзакция.
     */
    private fun rollRecipe(character: Character, level: Int, random: Random): features.logic.bench.BenchRecipe? {
        if (character.mapRecipeRolled || random.nextDouble() >= RECIPE_CHANCE) return null
        val found = features.logic.bench.CraftingBench.draw(character.knownBenchRecipes, level, random) ?: return null
        character.knownBenchRecipes.add(found.code)
        character.mapRecipeRolled = true
        return found
    }

    /** Сколько сундуков ещё стоит на карте у героя и когда их станет снова (с 0.31.0). */
    suspend fun chests(characterId: String, mapCode: String): ChestState {
        val method = "chests"
        val character = characters.requireCharacter(characterId, method)
        openMap(character, mapCode, method)
        val window = windowOf(character, mapCode, characterId, method)
        return ChestState(window.left, window.refreshAt, window.bought)
    }

    /** «Карта сокровищ» (0.34.0): ещё один сундук в текущем окне карты, раз за окно, за золото. */
    suspend fun treasure(characterId: String, mapCode: String): MapServiceOutcome {
        val method = "treasure"
        val character = characters.requireCharacter(characterId, method)
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
        val character = characters.requireCharacter(characterId, method)
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
        val character = characters.requireCharacter(characterId, method)
        val map = openMap(character, mapCode, method)
        val window = windowOf(character, mapCode, characterId, method)
        if (window.left <= 0) throw CampaignExceptions.funExceptionNoChest(method, mapCode)
        character.chests[mapCode] = window.copy(left = window.left - 1)
        val template = CampaignContent.file.chapters.flatMap { it.maps }.first { it.code == mapCode }
        return grant(character, characters.calculateStats(character).stats, CampaignContent.file.lootTables.getValue(template.chestLoot), map.level,
            CampaignChests.rarity(CampaignContent.file.chests), 0.0, method, mapCode = mapCode)
    }

    /** Окно сундуков карты; истёкшее бросается заново и сохраняется сразу. */
    private suspend fun windowOf(character: Character, mapCode: String, characterId: String, method: String): ChestWindow {
        val current = character.chests[mapCode]
        val window = CampaignChests.window(current, System.currentTimeMillis(), CampaignContent.file.chests,
            characters.calculateStats(character).stats[EnumStatStock.STOCK_CHEST_QUANTITY] ?: 0.0, Random.Default)
        if (window != current) {
            character.chests[mapCode] = window
            transactionExecute(method) { session -> characters.update(character, session) }
        }
        return window
    }


    /**
     * Добыча по таблице и опыт - одной транзакцией, как у промокода. Карта, с которой герой вошёл
     * в [mapCode], добавляет свои количество и редкость; с шансом [mapChance] (с 0.35.0) падает карта.
     */
    private suspend fun grant(character: Character, sheet: Map<application.enums.IntEnumStat, Double>, table: LootTable, level: Int, rarity: CampaignRarity,
                              experience: Double, method: String, extra: List<features.data.equipment.equipment_data.Equipment> = emptyList(),
                              mapCode: String, mapChance: Double = 0.0, recipeFound: features.logic.bench.BenchRecipe? = null): CampaignReward {
        val active = mapBonus(character, mapCode)
        fun bonus(stat: EnumStatStock) = sheet[stat] ?: 0.0
        val random = Random.Default
        val quantity = bonus(EnumStatStock.STOCK_QUANTITY) + active.quantity
        val loot = CampaignLoot.roll(table, level, rarity, quantity, bonus(EnumStatStock.STOCK_GOLD), random)
        val orbs = loot.orbs.mapNotNull { (code, amount) ->
            itemsCache.findByCode(code)?.let { CharacterItems(it._id, amount) }
        }
        // Экипировка тянется из пулов строки таблицы: что в них не состоит, отсюда не падает.
        val templates = extra + loot.equipment.mapNotNull { pools ->
            CampaignLoot.pick(equipmentCache.poolUpTo(pools, level), { it.rarity }, rarity.rarityBonus + bonus(EnumStatStock.STOCK_RARITY) + active.rarity, random)
        }
        val rule = CampaignContent.file.maps
        val dropped = CampaignMaps.drop(rule, mapChance * (1 + quantity / 100), mapCode, CampaignContent.maps.keys.toList(), random)
            ?.let { code -> equipmentCache.findByCode(CampaignMaps.templateCode(code)) }

        val equipment = transactionExecute(method) { session ->
            if (orbs.isNotEmpty()) characters.applyItems(character, orbs, method)
            if (experience > 0) characters.applyExperience(character, experience, method)
            character.money += loot.gold
            val created = inventory.addAllFromEquipment(character._id, templates, session) +
                listOfNotNull(dropped?.let { inventory.addRolled(character._id, it, CampaignMaps.rarity(rule, random), session) })
            characters.update(character, session)
            created
        }
        return CampaignReward(experience, loot.gold, orbs, equipment, character.level.toInt(), character.experience, character.money, recipeFound)
    }

    /**
     * Герой погиб на карте: часть опыта текущего уровня теряется по правилу [CombatRules.death].
     * Карта должна быть открыта, как и для убийства; на ранних картах правило ничего не отнимает.
     */
    suspend fun fall(characterId: String, mapCode: String): CampaignFall {
        val method = "fall"
        val character = characters.requireCharacter(characterId, method)
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
        val character = characters.requireCharacter(characterId, method)
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
}
