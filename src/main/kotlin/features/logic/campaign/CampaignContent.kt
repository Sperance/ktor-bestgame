package features.logic.campaign

import application.enums.EnumCurrencyOrb
import application.enums.EnumModifierOperation
import application.enums.EnumStatBool
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
/** Редкость монстра; `UNIQUE` (с 0.32.0) - только босс карты, случайно она не выпадает никогда. */
enum class EnumMonsterRarity { NORMAL, MAGIC, RARE, UNIQUE }

/** Что может выпасть: сфера из справочника валюты или экипировка уровня карты. */
enum class EnumLootKind { ORB, EQUIPMENT }

/** Одно изменение характеристики монстра: те же операции, что у модификаторов предметов. */
@Serializable
data class MonsterEffect(val stat: String, val operation: EnumModifierOperation, val value: Double)

/**
 * Правило редкости: насколько часто она выпадает на карте, сколько модификаторов получает
 * монстр, что она сама добавляет к его характеристикам и во сколько раз растут добыча и опыт.
 *
 * С 0.27.0 тир редкости поднимает **все** растущие характеристики монстра: [statScale] - это
 * «больше» в процентах к каждой из `growth` (здоровье, щит, урон всех типов, броня, уклонение,
 * регенерация, порог оглушения). Клиенту правило уходит уже развёрнутым в [effects], так что
 * формула у него одна. [modifierPower] - во сколько раз сильнее значения модификаторов монстра
 * этой редкости, а какие модификаторы ему доступны, говорит [MonsterModifier.minRarity].
 */
@Serializable
data class CampaignRarity(
    val rarity: EnumMonsterRarity,
    val weight: Int,
    val modifiers: List<Int>,
    val statScale: Double = 0.0,
    val modifierPower: Double = 1.0,
    val effects: List<MonsterEffect> = emptyList(),
    val quantity: Double = 1.0,
    val rarityBonus: Double = 0.0,
    val experience: Double = 1.0,
)

/**
 * Модификатор монстра. [minLevel] - с какого уровня карты он может выпасть, [minRarity] - с какой
 * редкости: у редкого монстра пул шире, чем у магического.
 */
@Serializable
data class MonsterModifier(
    val code: String,
    val weight: Int,
    val minLevel: Int = 1,
    val minRarity: EnumMonsterRarity = EnumMonsterRarity.MAGIC,
    val effects: List<MonsterEffect>,
)

/**
 * Недуг: что вешает попадание одного типа урона, как в PoE. Бой считает клиент, но числа - отсюда.
 *
 * [ailment] - имя из [application.enums.EnumStatBool] без `BOOL_` (BURNING, CHILLED, FROZEN,
 * SHOCKED, POISONED, BLEEDING); [type] - характеристика урона, которая его вешает
 * (`STOCK_ATTACK_FIRE`...). [chance] - шанс в процентах на попадание с уроном этого типа,
 * [threshold] - доля здоровья цели в процентах, которую это попадание должно снять (заморозка -
 * только сильным ударом). [magnitude]: у урона со временем - сколько процентов урона попадания
 * дотекает за [duration] секунд; у охлаждения - на сколько процентов замедлены действия; у шока -
 * на сколько процентов больше урона получает цель. Яд складывается стопками, остальные обновляются.
 */
@Serializable
data class AilmentRule(
    val ailment: String,
    val type: String,
    val chance: Double,
    val magnitude: Double = 0.0,
    val duration: Double,
    val threshold: Double = 0.0,
    val stacks: Boolean = false,
)

@Serializable data class UnarmedRule(val damage: Double, val speed: Double)
@Serializable data class CriticalRule(val chance: Double, val multiplier: Double)
/** Броня снижает физический урон на `armour / (armour + factor × damage)`, не больше [cap] процентов. */
@Serializable data class ArmourRule(val factor: Double, val cap: Double)
/** Шанс уклониться - `evasion / (evasion + base + perLevel × уровень атакующего)`, не больше [cap]. */
@Serializable data class EvasionRule(val base: Double, val perLevel: Double, val cap: Double)
/** Удар на [share] процентов здоровья цели сверх её порога оглушения откладывает её действие на [duration] секунд. */
@Serializable data class StunRule(val share: Double, val duration: Double)
/** Энергетический щит, не тронутый [rechargeDelay] секунд, восстанавливается на [rechargePerSecond] процентов в секунду. */
@Serializable data class ShieldRule(val rechargeDelay: Double, val rechargePerSecond: Double)
/**
 * Заклинание: у героя оно врождённое - [innateDamage] плюс [innatePerLevel] за уровень сверх
 * `STOCK_ATTACK_MAGICAL` листа, скорость сотворения [castSpeed] в секунду, если лист не даёт своей;
 * стоит [manaCost] процентов маны, а мана возвращается на [manaRegenShare] процентов в секунду.
 * Монстр колдует, только если у него есть `STOCK_ATTACK_MAGICAL` и `STOCK_MANA`.
 */
@Serializable data class SpellRule(val innateDamage: Double, val innatePerLevel: Double, val castSpeed: Double, val manaCost: Double, val manaRegenShare: Double)
/** Флакон жизни: [charges] зарядов на забег, [perKill] за убийство, лечит [heal] процентов здоровья за [duration] секунд. */
@Serializable data class FlaskRule(val charges: Int, val perKill: Int, val heal: Double, val duration: Double)
/** Отступление из боя занимает [delay] секунд, в которые герой не бьёт, а монстр - бьёт. */
@Serializable data class RetreatRule(val delay: Double)
/** Смерть на карте уровня от [fromLevel] стоит [experienceShare] процентов опыта текущего уровня; уровень не падает. */
@Serializable data class DeathRule(val fromLevel: Int, val experienceShare: Double)

/**
 * Правила боя - числа, по которым клиент считает автобой (с 0.28.0).
 *
 * Бой остаётся клиентским по решению владельца, но формулы и константы - сервера: клиент читает
 * их вместе с главами и не держит своих. [timeLimit] - секунды, после которых бой никто не выиграл;
 * [variance] - разброс урона удара в процентах; [resistCap], [blockCap] - потолки в процентах;
 * [spellBlockShare] - какая доля шанса блока работает против заклинаний.
 */
@Serializable
data class CombatRules(
    val timeLimit: Double,
    val variance: Double,
    val resistCap: Double,
    val blockCap: Double,
    val spellBlockShare: Double,
    val unarmed: UnarmedRule,
    val critical: CriticalRule,
    val armour: ArmourRule,
    val evasion: EvasionRule,
    val stun: StunRule,
    val shield: ShieldRule,
    val spell: SpellRule,
    val flask: FlaskRule,
    val retreat: RetreatRule,
    val death: DeathRule,
    val ailments: List<AilmentRule>,
)

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
    /** Своё поведение монстра поверх поведения его формы (с 0.30.0); обычно пусто. */
    val behaviour: BehaviourRule? = null,
    /** Босс карты (с 0.32.0): не бродит среди прочих, стоит у выхода и не катает редкость. */
    val boss: Boolean = false,
    /** Закреплённые модификаторы босса - коды из `modifiers`, одни и те же при каждой встрече. */
    val modifiers: List<String> = emptyList(),
    /** Уникалка, что падает только с этого босса. */
    val unique: String? = null,
)

/**
 * Боссы (с 0.32.0): страж выхода каждой карты. Убитый, он возвращается через [respawnHours] часов;
 * до тех пор выход открыт. С него падает добыча его таблицы с редкостью `UNIQUE`, с шансом
 * [uniqueChance] - случайная обычная уникалка, с шансом [ownUniqueChance] - его собственная.
 */
/**
 * Услуги карты за золото (с 0.34.0): «Карта сокровищ» - ещё один сундук в окне, раз за окно, за
 * [treasurePerLevel] × уровень карты; «Вызов стража» - убитый босс снова у выхода, за
 * [summonPerLevel] × уровень карты.
 */
@Serializable
data class ServiceRule(val treasurePerLevel: Long, val summonPerLevel: Long)

@Serializable
data class BossRule(val respawnHours: Double, val uniqueChance: Double, val ownUniqueChance: Double, val behaviour: BehaviourRule)

/** Босс карты, какой его видит клиент: характеристики и модификаторы уже подняты до уровня карты. */
@Serializable
data class CampaignBoss(val code: String, val form: String, val stats: Map<String, Double>, val behaviour: BehaviourRule, val modifiers: List<MonsterModifier>)

/**
 * Как монстр ведёт себя на карте, пока не начался бой (с 0.30.0). Ходит по карте клиент, но
 * числа - сервера, как и правила боя.
 *
 * [type]: `WANDER` бродит в [wanderRadius] клеток от дома; `PATROL` ходит между домом и точкой
 * патруля в [wanderRadius] клеток; `AMBUSH` стоит, пока герой не подойдёт на [wake] клеток;
 * `SLEEP` спит до тех же [wake] клеток, а проснувшись - бродит. Любой замечает героя, которого
 * видит напрямую не дальше [sight] клеток, гонится со скоростью [chaseSpeed] в обход стен и,
 * не видя его [giveUp] секунд, возвращается домой со скоростью [wanderSpeed].
 */
@Serializable
data class BehaviourRule(
    val type: String,
    val wanderSpeed: Double,
    val chaseSpeed: Double,
    val sight: Double,
    val wanderRadius: Double = 0.0,
    val wake: Double = 0.0,
    val giveUp: Double,
) {
    companion object { val types = setOf("WANDER", "PATROL", "AMBUSH", "SLEEP") }
}

/** Поведение по формам монстров и [default] для формы, которой в таблице нет. */
@Serializable
data class BehaviourTable(val default: BehaviourRule, val forms: Map<String, BehaviourRule> = emptyMap())

@Serializable
data class CampaignMapTemplate(
    val code: String,
    val biome: String,
    val level: Int,
    val monsters: List<String>,
    val count: List<Int>,
    /** Во сколько раз биом меняет радиус света героя (с 0.30.0): склеп темнее, берег светлее. */
    val light: Double = 1.0,
    /** Таблица добычи сундуков этой карты (с 0.31.0). */
    val chestLoot: String,
    /** Босс карты, страж её выхода (с 0.32.0). */
    val boss: String,
)

/**
 * Сундуки карты (с 0.31.0): у каждого героя на каждой карте своё окно в [refreshHours] часов. В
 * начале окна сервер бросает, сколько сундуков стоит на карте - от `count[0]` до `count[1]`, плюс
 * `STOCK_CHEST_QUANTITY` героя, - и столько раз за окно их можно открыть, сколько бы заходов ни
 * было. Добыча - таблица карты с множителем количества [quantity] и бонусом редкости [rarityBonus].
 */
@Serializable
data class ChestRule(val count: List<Int>, val refreshHours: Double, val quantity: Double, val rarityBonus: Double)

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
    val combat: CombatRules,
    val behaviour: BehaviourTable,
    val chests: ChestRule,
    val bosses: BossRule,
    val services: ServiceRule,
)

// ==================== То, что уходит клиенту ====================

/** Монстр карты: характеристики уже подняты до её уровня, клиенту остаётся только драться. */
@Serializable
data class CampaignMonster(val code: String, val form: String, val stats: Map<String, Double>, val behaviour: BehaviourRule)

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
    val light: Double,
    val monsters: List<CampaignMonster>,
    val modifiers: List<MonsterModifier>,
    val boss: CampaignBoss,
)

@Serializable
data class CampaignChapter(val code: String, val maps: List<CampaignMap>)

/** Главы, правила редкости и правила боя - всё, что клиенту нужно, чтобы драться, одним ответом. */
@Serializable
data class CampaignView(val chapters: List<CampaignChapter>, val rarities: List<CampaignRarity>, val combat: CombatRules, val services: ServiceRule)

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
                    light = map.light,
                    monsters = map.monsters.map { code ->
                        val template = monsters.getValue(code)
                        CampaignMonster(code, template.form, (content.defaults + template.stats).mapValues { (stat, value) -> scale(content, stat, value, map.level) },
                            template.behaviour ?: content.behaviour.forms[template.form] ?: content.behaviour.default)
                    },
                    modifiers = content.modifiers.filter { it.minLevel <= map.level }.map { raise(content, it, map.level) },
                    boss = monsters.getValue(map.boss).let { boss ->
                        CampaignBoss(boss.code, boss.form, (content.defaults + boss.stats).mapValues { (stat, value) -> scale(content, stat, value, map.level) },
                            boss.behaviour ?: content.bosses.behaviour,
                            boss.modifiers.map { code -> raise(content, content.modifiers.first { it.code == code }, map.level) })
                    },
                )
            })
        }
        // Тир редкости разворачивается в «больше» к каждой растущей характеристике.
        val rarities = content.rarities.map { rarity ->
            if (rarity.statScale <= 0) rarity
            else rarity.copy(effects = rarity.effects + content.growth.keys.map { MonsterEffect(it, EnumModifierOperation.MORE, rarity.statScale) })
        }
        return CampaignView(chapters, rarities, content.combat, content.services)
    }

    /** Модификатор монстра на уровне карты: растут только прибавки. */
    private fun raise(content: CampaignContentFile, modifier: MonsterModifier, level: Int): MonsterModifier =
        modifier.copy(effects = modifier.effects.map { effect ->
            if (effect.operation == EnumModifierOperation.ADD) effect.copy(value = scale(content, effect.stat, effect.value, level)) else effect
        })

    /** Уникалки, что падают только с боссов: их не даёт ни один другой источник. */
    val bossUniques: Set<String> by lazy { file.monsters.mapNotNull { it.unique }.toSet() }

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
            if (rarity.modifiers.size != 2 || rarity.modifiers[0] > rarity.modifiers[1] || rarity.modifierPower <= 0 || rarity.statScale < 0)
                throw CampaignExceptions.funExceptionContent(method, "rarity ${rarity.rarity}")
            rarity.effects.forEach { stat(it.stat) }
        }
        if (content.rarities.map { it.rarity }.toSet() != EnumMonsterRarity.entries.toSet()) throw CampaignExceptions.funExceptionContent(method, "rarities")
        content.modifiers.forEach { modifier ->
            modifier.effects.forEach { stat(it.stat) }
            if (modifier.minRarity == EnumMonsterRarity.NORMAL) throw CampaignExceptions.funExceptionContent(method, "modifier ${modifier.code} on a normal monster")
        }
        // У каждой редкости с модификаторами должно быть из чего выбирать.
        content.rarities.filter { it.modifiers[1] > 0 }.forEach { rarity ->
            if (content.modifiers.count { it.minRarity <= rarity.rarity } < rarity.modifiers[1]) throw CampaignExceptions.funExceptionContent(method, "pool of ${rarity.rarity}")
        }
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
        content.monsters.forEach { monster ->
            // Колдующий монстр без маны никогда бы не колдовал - это ошибка файла, а не тихий монстр.
            if ((monster.stats["STOCK_ATTACK_MAGICAL"] ?: 0.0) > 0 && (monster.stats["STOCK_MANA"] ?: 0.0) <= 0)
                throw CampaignExceptions.funExceptionContent(method, "caster ${monster.code} without mana")
        }
        val monsters = content.monsters.map { it.code }.toSet()
        if (monsters.size != content.monsters.size) throw CampaignExceptions.funExceptionContent(method, "monster codes")
        validate(content.combat)
        val codes = content.chapters.flatMap { chapter -> chapter.maps.map { it.code } }
        if (codes.toSet().size != codes.size) throw CampaignExceptions.funExceptionContent(method, "map codes")
        content.chapters.flatMap { it.maps }.forEach { map ->
            if (map.monsters.size !in 2..4) throw CampaignExceptions.funExceptionContent(method, "monsters of ${map.code}")
            map.monsters.forEach { if (it !in monsters) throw CampaignExceptions.funExceptionContent(method, "monster $it") }
            if (map.count.size != 2 || map.count[0] < 1 || map.count[0] > map.count[1]) throw CampaignExceptions.funExceptionContent(method, "count of ${map.code}")
            if (map.light <= 0) throw CampaignExceptions.funExceptionContent(method, "light of ${map.code}")
            if (map.chestLoot !in content.lootTables) throw CampaignExceptions.funExceptionContent(method, "chest loot of ${map.code}")
            if (content.monsters.none { it.code == map.boss && it.boss }) throw CampaignExceptions.funExceptionContent(method, "boss of ${map.code}")
            if (map.monsters.any { code -> content.monsters.first { it.code == code }.boss }) throw CampaignExceptions.funExceptionContent(method, "boss among monsters of ${map.code}")
        }
        val modifierCodes = content.modifiers.map { it.code }.toSet()
        content.monsters.filter { it.boss }.forEach { boss ->
            if (boss.unique == null || boss.modifiers.any { it !in modifierCodes }) throw CampaignExceptions.funExceptionContent(method, "boss ${boss.code}")
        }
        content.bosses.let { rule ->
            if (rule.respawnHours <= 0 || rule.uniqueChance !in 0.0..1.0 || rule.ownUniqueChance !in 0.0..1.0) throw CampaignExceptions.funExceptionContent(method, "bosses")
        }
        if (content.services.treasurePerLevel <= 0 || content.services.summonPerLevel <= 0) throw CampaignExceptions.funExceptionContent(method, "services")
        content.chests.let { rule ->
            if (rule.count.size != 2 || rule.count[0] < 0 || rule.count[0] > rule.count[1] || rule.refreshHours <= 0 || rule.quantity <= 0)
                throw CampaignExceptions.funExceptionContent(method, "chests")
        }
        val forms = content.monsters.map { it.form }.toSet()
        content.behaviour.forms.keys.forEach { if (it !in forms) throw CampaignExceptions.funExceptionContent(method, "behaviour of form $it") }
        (listOf(content.behaviour.default, content.bosses.behaviour) + content.behaviour.forms.values + content.monsters.mapNotNull { it.behaviour }).forEach(::validate)
    }

    private fun validate(rule: BehaviourRule) {
        val method = "BehaviourRule"
        if (rule.type !in BehaviourRule.types) throw CampaignExceptions.funExceptionContent(method, "type ${rule.type}")
        if (rule.chaseSpeed <= 0 || rule.wanderSpeed < 0 || rule.sight <= 0 || rule.giveUp <= 0 || rule.wanderRadius < 0 || rule.wake < 0)
            throw CampaignExceptions.funExceptionContent(method, rule.type)
        if (rule.type in setOf("AMBUSH", "SLEEP") && rule.wake <= 0) throw CampaignExceptions.funExceptionContent(method, "${rule.type} without wake")
        if (rule.type in setOf("WANDER", "PATROL") && (rule.wanderSpeed <= 0 || rule.wanderRadius <= 0)) throw CampaignExceptions.funExceptionContent(method, "${rule.type} standing still")
    }

    private fun validate(rules: CombatRules) {
        val method = "CombatRules"
        val ailments = EnumStatBool.entries.map { it.name.removePrefix("BOOL_") }.toSet()
        val damage = EnumStatStock.entries.map { it.name }.filter { it.startsWith("STOCK_ATTACK_") }.toSet()
        fun positive(value: Double, name: String) { if (value <= 0) throw CampaignExceptions.funExceptionContent(method, name) }
        fun percent(value: Double, name: String) { if (value !in 0.0..100.0) throw CampaignExceptions.funExceptionContent(method, name) }
        positive(rules.timeLimit, "timeLimit"); percent(rules.variance, "variance")
        percent(rules.resistCap, "resistCap"); percent(rules.blockCap, "blockCap"); percent(rules.spellBlockShare, "spellBlockShare")
        positive(rules.unarmed.damage, "unarmed.damage"); positive(rules.unarmed.speed, "unarmed.speed")
        percent(rules.critical.chance, "critical.chance"); if (rules.critical.multiplier < 100) throw CampaignExceptions.funExceptionContent(method, "critical.multiplier")
        positive(rules.armour.factor, "armour.factor"); percent(rules.armour.cap, "armour.cap")
        positive(rules.evasion.base, "evasion.base"); percent(rules.evasion.cap, "evasion.cap")
        if (rules.evasion.perLevel < 0 || rules.stun.share < 0 || rules.stun.duration < 0) throw CampaignExceptions.funExceptionContent(method, "stun")
        if (rules.shield.rechargeDelay < 0 || rules.shield.rechargePerSecond < 0) throw CampaignExceptions.funExceptionContent(method, "shield")
        if (rules.spell.innateDamage < 0 || rules.spell.innatePerLevel < 0) throw CampaignExceptions.funExceptionContent(method, "spell.innate")
        positive(rules.spell.castSpeed, "spell.castSpeed"); percent(rules.spell.manaCost, "spell.manaCost"); percent(rules.spell.manaRegenShare, "spell.manaRegenShare")
        if (rules.flask.charges < 0 || rules.flask.perKill < 0 || rules.flask.duration <= 0) throw CampaignExceptions.funExceptionContent(method, "flask")
        percent(rules.flask.heal, "flask.heal")
        if (rules.retreat.delay < 0) throw CampaignExceptions.funExceptionContent(method, "retreat.delay")
        if (rules.death.fromLevel < 1) throw CampaignExceptions.funExceptionContent(method, "death.fromLevel")
        percent(rules.death.experienceShare, "death.experienceShare")
        rules.ailments.forEach { rule ->
            if (rule.ailment !in ailments) throw CampaignExceptions.funExceptionContent(method, "ailment ${rule.ailment}")
            if (rule.type !in damage) throw CampaignExceptions.funExceptionContent(method, "ailment ${rule.ailment} by ${rule.type}")
            percent(rule.chance, "ailment ${rule.ailment} chance"); percent(rule.threshold, "ailment ${rule.ailment} threshold")
            if (rule.magnitude < 0 || rule.duration <= 0) throw CampaignExceptions.funExceptionContent(method, "ailment ${rule.ailment}")
        }
        if (rules.ailments.map { it.ailment }.toSet().size != rules.ailments.size) throw CampaignExceptions.funExceptionContent(method, "ailments")
    }
}
