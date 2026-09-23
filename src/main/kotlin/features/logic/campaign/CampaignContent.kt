package features.logic.campaign

import application.enums.EnumCurrencyOrb
import application.enums.EnumModifierOperation
import application.enums.EnumStatStock
import base.exception.model.CampaignExceptions
import config.ContentResource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow

/**
 * Редкость монстра - как в PoE: обычный, магический с одним-двумя модификаторами и редкий с
 * тремя-четырьмя. Катает её клиент по весам [CampaignRarity], а сервер по ней умножает добычу и опыт.
 */
enum class EnumMonsterRarity { NORMAL, MAGIC, RARE }

/** Что может выпасть: сфера из справочника валюты или экипировка уровня карты. */
enum class EnumLootKind { ORB, EQUIPMENT }

/** Одно изменение характеристики монстра: те же операции, что у модификаторов предметов. */
@Serializable
data class MonsterEffect(val stat: String, val operation: EnumModifierOperation, val value: Double)

/**
 * Правило редкости: насколько часто она выпадает на карте, сколько модификаторов получает
 * монстр, что она сама добавляет к его характеристикам и во сколько раз растут добыча и опыт.
 */
@Serializable
data class CampaignRarity(
    val rarity: EnumMonsterRarity,
    val weight: Int,
    val modifiers: List<Int>,
    val effects: List<MonsterEffect> = emptyList(),
    val quantity: Double = 1.0,
    val rarityBonus: Double = 0.0,
    val experience: Double = 1.0,
)

/** Модификатор монстра. [minLevel] - с какого уровня карты он может выпасть. */
@Serializable
data class MonsterModifier(val code: String, val weight: Int, val minLevel: Int = 1, val effects: List<MonsterEffect>)

@Serializable
data class LootDrop(val kind: EnumLootKind, val code: String = "", val chance: Double, val amount: List<Long> = listOf(1, 1))

@Serializable
data class LootTable(val gold: List<Long>, val drops: List<LootDrop>)

/**
 * Монстр на первом уровне. На карте его характеристики растут по [CampaignContentFile.growth],
 * а [form] говорит клиенту, каким силуэтом его рисовать.
 */
@Serializable
data class MonsterTemplate(
    val code: String,
    val form: String,
    val experience: Double,
    val loot: String,
    val stats: Map<String, Double>,
)

@Serializable
data class CampaignMapTemplate(
    val code: String,
    val biome: String,
    val level: Int,
    val monsters: List<String>,
    val count: List<Int>,
)

@Serializable
data class CampaignChapterTemplate(val code: String, val maps: List<CampaignMapTemplate>)

@Serializable
data class CampaignContentFile(
    val defaults: Map<String, Double> = emptyMap(),
    val growth: Map<String, Double> = emptyMap(),
    val rarities: List<CampaignRarity>,
    val modifiers: List<MonsterModifier>,
    val lootTables: Map<String, LootTable>,
    val monsters: List<MonsterTemplate>,
    val chapters: List<CampaignChapterTemplate>,
)

// ==================== То, что уходит клиенту ====================

/** Монстр карты: характеристики уже подняты до её уровня, клиенту остаётся только драться. */
@Serializable
data class CampaignMonster(val code: String, val form: String, val stats: Map<String, Double>)

/**
 * Карта главы, какой её видит клиент.
 *
 * Модификаторы тоже подняты до уровня карты: «+3 к урону огнём» на первой карте и на
 * двадцатой - разные числа, и считать рост второй раз на клиенте было бы второй копией правила.
 */
@Serializable
data class CampaignMap(
    val code: String,
    val chapter: String,
    val order: Int,
    val biome: String,
    val level: Int,
    val monsterCount: List<Int>,
    val monsters: List<CampaignMonster>,
    val modifiers: List<MonsterModifier>,
)

@Serializable
data class CampaignChapter(val code: String, val maps: List<CampaignMap>)

@Serializable
data class CampaignView(val chapters: List<CampaignChapter>, val rarities: List<CampaignRarity>)

/**
 * Содержимое кампании - главы, карты, монстры, их модификаторы и добыча.
 *
 * Лежит в `resources/content/campaign.json` и в базу не пишется: это правила мира, а не
 * состояние, и перечитываются они при старте. Файл проверяется при чтении, как и остальные
 * таблицы: неизвестная характеристика или монстр без таблицы добычи - ошибка старта, а не
 * пустой бой.
 */
object CampaignContent {

    const val FILE = "campaign.json"

    private val json = Json { ignoreUnknownKeys = true }

    val file: CampaignContentFile by lazy { load(ContentResource.read(FILE)) }

    val view: CampaignView by lazy { resolve(file) }

    val maps: Map<String, CampaignMap> by lazy { view.chapters.flatMap { it.maps }.associateBy { it.code } }

    val monsters: Map<String, MonsterTemplate> by lazy { file.monsters.associateBy { it.code } }

    fun load(text: String): CampaignContentFile = json.decodeFromString(CampaignContentFile.serializer(), text).also(::validate)

    /**
     * Порядок карт в главе - порядок их открытия: следующая открывается, когда пройдена предыдущая.
     */
    fun unlocked(cleared: Collection<String>): List<String> = view.chapters.flatMap { chapter ->
        chapter.maps.filterIndexed { index, _ -> index == 0 || chapter.maps[index - 1].code in cleared }.map { it.code }
    }

    fun resolve(content: CampaignContentFile): CampaignView {
        val monsters = content.monsters.associateBy { it.code }
        val chapters = content.chapters.map { chapter ->
            CampaignChapter(chapter.code, chapter.maps.mapIndexed { index, map ->
                CampaignMap(
                    code = map.code,
                    chapter = chapter.code,
                    order = index + 1,
                    biome = map.biome,
                    level = map.level,
                    monsterCount = map.count,
                    monsters = map.monsters.map { code ->
                        val template = monsters.getValue(code)
                        CampaignMonster(code, template.form, (content.defaults + template.stats).mapValues { (stat, value) -> scale(content, stat, value, map.level) })
                    },
                    modifiers = content.modifiers.filter { it.minLevel <= map.level }.map { modifier ->
                        modifier.copy(effects = modifier.effects.map { effect ->
                            if (effect.operation == EnumModifierOperation.ADD) effect.copy(value = scale(content, effect.stat, effect.value, map.level)) else effect
                        })
                    },
                )
            })
        }
        return CampaignView(chapters, content.rarities)
    }

    /** Характеристика на уровне карты: растёт только то, что названо в `growth`, и растёт степенью. */
    fun scale(content: CampaignContentFile, stat: String, value: Double, level: Int): Double {
        val factor = content.growth[stat] ?: return value
        return Math.round(value * factor.pow(level - 1) * 100.0) / 100.0
    }

    private fun validate(content: CampaignContentFile) {
        val method = "CampaignContent"
        val stats = EnumStatStock.entries.map { it.name }.toSet()
        val orbs = EnumCurrencyOrb.entries.map { it.name }.toSet()
        fun stat(name: String) { if (name !in stats) throw CampaignExceptions.funExceptionContent(method, "stat $name") }

        (content.defaults.keys + content.growth.keys).forEach(::stat)
        content.rarities.forEach { rarity ->
            if (rarity.modifiers.size != 2 || rarity.modifiers[0] > rarity.modifiers[1]) throw CampaignExceptions.funExceptionContent(method, "rarity ${rarity.rarity}")
            rarity.effects.forEach { stat(it.stat) }
        }
        if (content.rarities.map { it.rarity }.toSet() != EnumMonsterRarity.entries.toSet()) throw CampaignExceptions.funExceptionContent(method, "rarities")
        content.modifiers.forEach { modifier -> modifier.effects.forEach { stat(it.stat) } }
        content.lootTables.forEach { (name, table) ->
            if (table.gold.size != 2 || table.gold[0] > table.gold[1]) throw CampaignExceptions.funExceptionContent(method, "gold $name")
            table.drops.forEach { drop ->
                if (drop.kind == EnumLootKind.ORB && drop.code !in orbs) throw CampaignExceptions.funExceptionContent(method, "orb ${drop.code}")
                if (drop.chance !in 0.0..1.0 || drop.amount.size != 2 || drop.amount[0] > drop.amount[1]) throw CampaignExceptions.funExceptionContent(method, "drop $name")
            }
        }
        content.monsters.forEach { monster ->
            monster.stats.keys.forEach(::stat)
            if (monster.loot !in content.lootTables) throw CampaignExceptions.funExceptionContent(method, "loot ${monster.loot}")
        }
        val monsters = content.monsters.map { it.code }.toSet()
        if (monsters.size != content.monsters.size) throw CampaignExceptions.funExceptionContent(method, "monster codes")
        val codes = content.chapters.flatMap { chapter -> chapter.maps.map { it.code } }
        if (codes.toSet().size != codes.size) throw CampaignExceptions.funExceptionContent(method, "map codes")
        content.chapters.flatMap { it.maps }.forEach { map ->
            if (map.monsters.size !in 2..4) throw CampaignExceptions.funExceptionContent(method, "monsters of ${map.code}")
            map.monsters.forEach { if (it !in monsters) throw CampaignExceptions.funExceptionContent(method, "monster $it") }
            if (map.count.size != 2 || map.count[0] < 1 || map.count[0] > map.count[1]) throw CampaignExceptions.funExceptionContent(method, "count of ${map.code}")
        }
    }
}
