package features.logic.campaign

import application.enums.EnumCurrencyOrb
import application.enums.EnumModifierOperation
import application.enums.EnumRarity
import application.enums.EnumStatBool
import application.enums.EnumStatStock
import base.exception.model.CampaignExceptions
import config.ContentResource
import config.PoolSeeder
import features.logic.pools.EnumPoolTarget
import features.logic.pools.PoolTable
import features.logic.pools.Pooled
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow

/**
 * Редкость монстра - как в PoE: обычный, магический с одним-двумя модификаторами и редкий с
 * тремя-четырьмя. Катает её клиент по весам [CampaignRarity], а сервер по ней умножает добычу и опыт.
 */
/** Редкость монстра; `UNIQUE` (с 0.32.0) - только босс карты, случайно она не выпадает никогда. */
enum class EnumMonsterRarity { NORMAL, MAGIC, RARE, UNIQUE }

/**
 * Где монстр стоит в бою (с 0.61.0): ближний - в первом ряду, дальний - во втором и бьёт героя с
 * первой секунды. Героя с оружием ближнего боя второй ряд не подпускает, пока жив первый.
 */
enum class EnumMonsterRange { MELEE, RANGED }

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
 * Модификатор монстра в файле. [minLevel] - с какого уровня карты он может выпасть, [minRarity] - с
 * какой редкости: у редкого монстра пул шире, чем у магического. Карта называет пулы, из которых
 * роллятся её монстры, а их состав с 0.56.0 лежит в пулах вида MONSTER (`pools.json`).
 */
@Serializable
data class MonsterModifierRecord(
    override val code: String,
    val minLevel: Int = 1,
    val minRarity: EnumMonsterRarity = EnumMonsterRarity.MAGIC,
    val effects: List<MonsterEffect>,
) : Pooled

/**
 * Модификатор монстра, каким его видит клиент: пул карты уже разрешён, [weight] - вес в нём.
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
 * [heroChance] (с 0.36.0) - база героя вместо [chance], как в PoE: поджечь, шокировать, отравить и
 * пустить кровь герой может только шансом со снаряжения; null - та же база, что у монстров.
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
    val heroChance: Double? = null,
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
/** Отступление из боя занимает [delay] секунд, в которые герой не бьёт, а монстр - бьёт. */
@Serializable data class RetreatRule(val delay: Double)
/** Смерть на карте уровня от [fromLevel] стоит [experienceShare] процентов опыта текущего уровня; уровень не падает. */
@Serializable data class DeathRule(val fromLevel: Int, val experienceShare: Double)

/**
 * Правила боя - числа, по которым клиент считает автобой (с 0.28.0).
 *
 * Бой остаётся клиентским по решению владельца, но формулы и константы - сервера: клиент читает
 * их вместе с главами и не держит своих. [timeLimit] - секунды, после которых бой никто не выиграл;
 * [variance] - разброс урона удара в процентах; [resistCap], [blockCap] - потолки в процентах.
 * С 0.36.0: [resistHardCap] - выше него не поднимет никакой «+% к максимуму сопротивления»,
 * [ailmentDurationCap] - сильнее этого не сократить длительность состояния на себе.
 */
@Serializable
data class CombatRules(
    val timeLimit: Double,
    val variance: Double,
    val resistCap: Double,
    val blockCap: Double,
    val unarmed: UnarmedRule,
    val critical: CriticalRule,
    val armour: ArmourRule,
    val evasion: EvasionRule,
    val stun: StunRule,
    val shield: ShieldRule,
    val retreat: RetreatRule,
    val death: DeathRule,
    val ailments: List<AilmentRule>,
    val resistHardCap: Double = 90.0,
    val ailmentDurationCap: Double = 75.0,
)

/** Одна строка таблицы добычи; экипировка тянется из [equipmentPools] (с 0.39.0). */
@Serializable
data class LootDrop(val kind: EnumLootKind, val code: String = "", val chance: Double, val amount: List<Long> = listOf(1, 1),
                    val equipmentPools: List<String> = emptyList())

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
    /** Пулы собственных уникалок босса (с 0.39.0) - обычно `boss:<его код>`. */
    val uniquePools: List<String> = emptyList(),
    /** Страж осквернённой зоны (с 0.46.0): как босс, но не у выхода - его ставит клиент по зерну карты. */
    val corrupted: Boolean = false,
    /** Ближний или дальний бой (с 0.61.0); без него решает форма - см. [CampaignContentFile.rangedForms]. */
    val range: EnumMonsterRange? = null,
)

/**
 * Боссы (с 0.32.0): страж выхода каждой карты. Убитый, он возвращается через [respawnHours] часов;
 * до тех пор выход открыт. С него падает добыча его таблицы с редкостью `UNIQUE`, с шансом
 * [uniqueChance] - уникалка из [uniquePools], с шансом [ownUniqueChance] - из собственных пулов босса.
 */
/**
 * Услуги карты за золото (с 0.34.0): «Карта сокровищ» - ещё один сундук в окне, раз за окно, за
 * [treasurePerLevel] × уровень карты; «Вызов стража» - убитый босс снова у выхода, за
 * [summonPerLevel] × уровень карты.
 */
@Serializable
data class ServiceRule(val treasurePerLevel: Long, val summonPerLevel: Long)

/**
 * Фонтаны (с 0.43.0): на карте их от `count[0]` до `count[1]`, место и число - по зерну карты у
 * клиента, и каждый один раз лечит [heal] процентов максимума здоровья. Бой клиентский, поэтому и
 * фонтан пьёт клиент; сервер только называет правило.
 */
@Serializable
data class FountainRule(val count: List<Int> = listOf(0, 2), val heal: Double = 30.0)

/**
 * Карты (с 0.35.0): предмет слота `MAP` со своим уровнем, что открывает одну локацию того же уровня
 * с модификаторами. С обычного монстра карта падает с шансом [dropChance] (умноженным на количество
 * его редкости и героя), с босса - [bossChance]; с шансом [nextChance] она на уровень выше карты,
 * где упала. Редкость упавшей карты - по весам [rarities]. [risk] - сколько процентов к количеству,
 * редкости и опыту добычи даёт единица каждого вредного модификатора: чем опаснее карта, тем
 * щедрее. Клиенту правило уходит целиком, чтобы окно запуска показало ту же сумму, что начислит сервер.
 */
@Serializable
data class MapRule(
    val dropChance: Double,
    val bossChance: Double,
    val nextChance: Double,
    val rarities: Map<EnumRarity, Int>,
    val risk: Map<String, Double>,
    /** Сколько процентов к количеству и редкости добычи даёт сама редкость карты (с 0.42.0). */
    val rarityBonus: Map<EnumRarity, Double> = emptyMap(),
)

@Serializable
data class BossRule(val respawnHours: Double, val uniqueChance: Double, val ownUniqueChance: Double, val behaviour: BehaviourRule,
                    val uniquePools: List<String> = emptyList())

/** Босс карты, какой его видит клиент: характеристики и модификаторы уже подняты до уровня карты. */
@Serializable
data class CampaignBoss(val code: String, val form: String, val stats: Map<String, Double>, val behaviour: BehaviourRule, val modifiers: List<MonsterModifier>,
                        val range: EnumMonsterRange = EnumMonsterRange.MELEE)

/**
 * Осквернённая зона (с 0.46.0): случайный портал на карте (0-1 за заход, по шансу [chance] и
 * зерну у клиента), а за ним - страж [uniquePools] и [uniqueChance] на уникалку, как у мелкого
 * босса. Общий на несколько локаций одного уровня (см. `corrupted` карты, как `chestLoot`),
 * поэтому отдельного `respawnHours` у него нет - открыт он один раз за заход, а не до тех пор,
 * пока жив.
 */
@Serializable
data class CorruptionRule(val chance: Double = 0.0, val uniqueChance: Double = 0.0, val uniquePools: List<String> = emptyList())

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
    /** Сторона карты в клетках (с 0.40.0): клиент режет карту такого размера, 72 по умолчанию. */
    val size: Int = 72,
    /** Таблица добычи сундуков этой карты (с 0.31.0). */
    val chestLoot: String,
    /** Босс карты, страж её выхода (с 0.32.0). */
    val boss: String,
    /** Пулы модификаторов монстров этой карты (с 0.39.0). */
    val modifierPools: List<String> = emptyList(),
    /** Страж осквернённой зоны этой карты (с 0.46.0), если она выпадет за этот заход. */
    val corrupted: String,
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
    val modifiers: List<MonsterModifierRecord>,
    val lootTables: Map<String, LootTable>,
    val monsters: List<MonsterTemplate>,
    val chapters: List<CampaignChapterTemplate>,
    val combat: CombatRules,
    val behaviour: BehaviourTable,
    val chests: ChestRule,
    val bosses: BossRule,
    val services: ServiceRule,
    val maps: MapRule,
    val fountains: FountainRule = FountainRule(),
    val corruption: CorruptionRule = CorruptionRule(),
    val vaal: VaalRule = VaalRule(),
    /** Формы, которые дерутся издалека, если шаблон не сказал сам (с 0.61.0). */
    val rangedForms: Set<String> = emptySet(),
) {
    /** Ряд монстра в бою: свой из шаблона, иначе по форме. */
    fun range(template: MonsterTemplate): EnumMonsterRange =
        template.range ?: if (template.form in rangedForms) EnumMonsterRange.RANGED else EnumMonsterRange.MELEE
}

// ==================== То, что уходит клиенту ====================

/** Монстр карты: характеристики уже подняты до её уровня, клиенту остаётся только драться. */
@Serializable
data class CampaignMonster(val code: String, val form: String, val stats: Map<String, Double>, val behaviour: BehaviourRule,
                           val range: EnumMonsterRange = EnumMonsterRange.MELEE)

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
    val size: Int = 72,
    val light: Double,
    val monsters: List<CampaignMonster>,
    val modifiers: List<MonsterModifier>,
    val boss: CampaignBoss,
    /** Страж этой карты за осквернённым порталом (с 0.46.0), если он ей достался этим заходом. */
    val corrupted: CampaignBoss,
)

@Serializable
data class CampaignChapter(val code: String, val maps: List<CampaignMap>)

/** Главы, правила редкости и правила боя - всё, что клиенту нужно, чтобы драться, одним ответом. */
@Serializable
data class CampaignView(
    val chapters: List<CampaignChapter>,
    val rarities: List<CampaignRarity>,
    val combat: CombatRules,
    val services: ServiceRule,
    val maps: MapRule,
    val fountains: FountainRule = FountainRule(),
    val corruption: CorruptionRule = CorruptionRule(),
    val vaal: VaalRule = VaalRule(),
)

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

    /** Коды всех карт кампании. */
    val mapCodes: Set<String> by lazy { file.chapters.flatMapTo(mutableSetOf()) { chapter -> chapter.maps.map { it.code } } }

    private class Resolved(val pools: PoolTable, val view: CampaignView) {
        val maps: Map<String, CampaignMap> = view.chapters.flatMap { it.maps }.associateBy { it.code }
    }

    private val resolved = AtomicReference<Resolved?>(null)

    /**
     * Кампания, как её видит клиент: пулы модификаторов монстров разрешены по таблице [pools].
     * Таблица у кеша пулов одна на ревизию, поэтому вид пересобирается, лишь когда пулы правили.
     */
    fun view(pools: PoolTable): CampaignView = resolvedFor(pools).view

    /** Карта кампании по коду в том же виде. */
    fun map(code: String, pools: PoolTable): CampaignMap? = resolvedFor(pools).maps[code]

    private fun resolvedFor(pools: PoolTable): Resolved =
        resolved.get()?.takeIf { it.pools === pools } ?: Resolved(pools, resolve(file, pools)).also(resolved::set)

    val monsters: Map<String, MonsterTemplate> by lazy { file.monsters.associateBy { it.code } }

    fun load(text: String): CampaignContentFile = json.decodeFromString(CampaignContentFile.serializer(), text).also(::validate)

    /**
     * Порядок карт в главе - порядок их открытия: следующая открывается, когда пройдена предыдущая.
     */
    fun unlocked(cleared: Collection<String>): List<String> = file.chapters.flatMap { chapter ->
        chapter.maps.filterIndexed { index, _ -> index == 0 || chapter.maps[index - 1].code in cleared }.map { it.code }
    }

    fun resolve(content: CampaignContentFile, pools: PoolTable): CampaignView {
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
                    size = map.size,
                    light = map.light,
                    monsters = map.monsters.map { code ->
                        val template = monsters.getValue(code)
                        CampaignMonster(code, template.form, (content.defaults + template.stats).mapValues { (stat, value) -> scale(content, stat, value, map.level) },
                            template.behaviour ?: content.behaviour.forms[template.form] ?: content.behaviour.default, content.range(template))
                    },
                    modifiers = pools.of(content.modifiers.filter { it.minLevel <= map.level }, map.modifierPools)
                        .map { (modifier, weight) -> raise(content, modifier, weight, map.level) },
                    boss = guardian(content, pools, monsters, map.boss, map, content.bosses.behaviour),
                    corrupted = guardian(content, pools, monsters, map.corrupted, map, content.bosses.behaviour),
                )
            })
        }
        // Тир редкости разворачивается в «больше» к каждой растущей характеристике.
        val rarities = content.rarities.map { rarity ->
            if (rarity.statScale <= 0) rarity
            else rarity.copy(effects = rarity.effects + content.growth.keys.map { MonsterEffect(it, EnumModifierOperation.MORE, rarity.statScale) })
        }
        return CampaignView(chapters, rarities, content.combat, content.services, content.maps, content.fountains, content.corruption, content.vaal)
    }

    /** Босс или страж осквернённой зоны: та же форма ответа, характеристики подняты до уровня карты. */
    private fun guardian(content: CampaignContentFile, pools: PoolTable, monsters: Map<String, MonsterTemplate>, code: String, map: CampaignMapTemplate, defaultBehaviour: BehaviourRule): CampaignBoss =
        monsters.getValue(code).let { boss ->
            CampaignBoss(boss.code, boss.form, (content.defaults + boss.stats).mapValues { (stat, value) -> scale(content, stat, value, map.level) },
                boss.behaviour ?: defaultBehaviour,
                boss.modifiers.map { modCode -> content.modifiers.first { it.code == modCode }.let { raise(content, it, pools.weight(it.code, map.modifierPools), map.level) } },
                content.range(boss))
        }

    /** Модификатор монстра на уровне карты: растут только прибавки. */
    private fun raise(content: CampaignContentFile, modifier: MonsterModifierRecord, weight: Int, level: Int): MonsterModifier =
        MonsterModifier(modifier.code, weight, modifier.minLevel, modifier.minRarity, modifier.effects.map { effect ->
            if (effect.operation == EnumModifierOperation.ADD) effect.copy(value = scale(content, effect.stat, effect.value, level)) else effect
        })

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
        // У каждой редкости с модификаторами на каждой карте должно быть из чего выбирать - по пулам из файла.
        val monsterPools = PoolSeeder.table(EnumPoolTarget.MONSTER)
        content.chapters.flatMap { it.maps }.forEach { map ->
            val pool = monsterPools.of(content.modifiers.filter { it.minLevel <= map.level }, map.modifierPools).map { it.value }
            content.rarities.filter { it.modifiers[1] > 0 }.forEach { rarity ->
                if (pool.count { it.minRarity <= rarity.rarity } < rarity.modifiers[1]) throw CampaignExceptions.funExceptionContent(method, "pool of ${rarity.rarity} on ${map.code}")
            }
        }
        content.lootTables.forEach { (name, table) ->
            if (table.gold.size != 2 || table.gold[0] > table.gold[1]) throw CampaignExceptions.funExceptionContent(method, "gold $name")
            table.drops.forEach { drop ->
                if (drop.kind == EnumLootKind.ORB && drop.code !in orbs) throw CampaignExceptions.funExceptionContent(method, "orb ${drop.code}")
                if (drop.chance !in 0.0..1.0 || drop.amount.size != 2 || drop.amount[0] > drop.amount[1]) throw CampaignExceptions.funExceptionContent(method, "drop $name")
                if (drop.kind == EnumLootKind.EQUIPMENT && drop.equipmentPools.isEmpty()) throw CampaignExceptions.funExceptionContent(method, "equipment pools of $name")
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
            if (map.size !in 32..160) throw CampaignExceptions.funExceptionContent(method, "size of ${map.code}")
            if (map.modifierPools.isEmpty()) throw CampaignExceptions.funExceptionContent(method, "modifier pools of ${map.code}")
            if (map.chestLoot !in content.lootTables) throw CampaignExceptions.funExceptionContent(method, "chest loot of ${map.code}")
            if (content.monsters.none { it.code == map.boss && it.boss }) throw CampaignExceptions.funExceptionContent(method, "boss of ${map.code}")
            if (map.monsters.any { code -> content.monsters.first { it.code == code }.boss }) throw CampaignExceptions.funExceptionContent(method, "boss among monsters of ${map.code}")
            if (content.monsters.none { it.code == map.corrupted && it.corrupted }) throw CampaignExceptions.funExceptionContent(method, "corrupted guardian of ${map.code}")
            if (map.monsters.any { code -> content.monsters.first { it.code == code }.corrupted }) throw CampaignExceptions.funExceptionContent(method, "corrupted guardian among monsters of ${map.code}")
        }
        val modifierCodes = content.modifiers.map { it.code }.toSet()
        content.monsters.filter { it.boss }.forEach { boss ->
            if (boss.uniquePools.isEmpty() || boss.modifiers.any { it !in modifierCodes }) throw CampaignExceptions.funExceptionContent(method, "boss ${boss.code}")
        }
        content.monsters.filter { it.corrupted }.forEach { guardian ->
            if (guardian.boss) throw CampaignExceptions.funExceptionContent(method, "corrupted boss ${guardian.code}")
            if (guardian.uniquePools.isNotEmpty() || guardian.modifiers.any { it !in modifierCodes }) throw CampaignExceptions.funExceptionContent(method, "corrupted ${guardian.code}")
        }
        content.bosses.let { rule ->
            if (rule.respawnHours <= 0 || rule.uniqueChance !in 0.0..1.0 || rule.ownUniqueChance !in 0.0..1.0 || rule.uniquePools.isEmpty()) throw CampaignExceptions.funExceptionContent(method, "bosses")
        }
        if (content.services.treasurePerLevel <= 0 || content.services.summonPerLevel <= 0) throw CampaignExceptions.funExceptionContent(method, "services")
        content.fountains.let { rule ->
            if (rule.count.size != 2 || rule.count[0] < 0 || rule.count[0] > rule.count[1] || rule.heal !in 0.0..100.0)
                throw CampaignExceptions.funExceptionContent(method, "fountains")
        }
        content.corruption.let { rule ->
            if (rule.chance !in 0.0..1.0 || rule.uniqueChance !in 0.0..1.0 || rule.uniquePools.isEmpty())
                throw CampaignExceptions.funExceptionContent(method, "corruption")
        }
        content.vaal.let { rule ->
            if (rule.mods.size != 2 || rule.mods[0] < 1 || rule.mods[0] > rule.mods[1] || rule.mods[1] > rule.pool.size || rule.power <= 0 || rule.reward < 0 || rule.perMod < 0
                || rule.pool.any { it.weight <= 0 } || rule.pool.map { it.modifier }.toSet().size != rule.pool.size)
                throw CampaignExceptions.funExceptionContent(method, "vaal")
        }
        content.maps.let { rule ->
            if (listOf(rule.dropChance, rule.bossChance, rule.nextChance).any { it !in 0.0..1.0 }) throw CampaignExceptions.funExceptionContent(method, "maps")
            if (rule.rarities.isEmpty() || rule.rarities.values.any { it <= 0 }) throw CampaignExceptions.funExceptionContent(method, "maps.rarities")
            rule.risk.forEach { (name, weight) -> stat(name); if (weight <= 0) throw CampaignExceptions.funExceptionContent(method, "maps.risk $name") }
            if (rule.rarityBonus.values.any { it < 0 }) throw CampaignExceptions.funExceptionContent(method, "maps.rarityBonus")
        }
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
        percent(rules.resistCap, "resistCap"); percent(rules.blockCap, "blockCap")
        percent(rules.resistHardCap, "resistHardCap"); percent(rules.ailmentDurationCap, "ailmentDurationCap")
        if (rules.resistHardCap < rules.resistCap) throw CampaignExceptions.funExceptionContent(method, "resistHardCap")
        positive(rules.unarmed.damage, "unarmed.damage"); positive(rules.unarmed.speed, "unarmed.speed")
        percent(rules.critical.chance, "critical.chance"); if (rules.critical.multiplier < 100) throw CampaignExceptions.funExceptionContent(method, "critical.multiplier")
        positive(rules.armour.factor, "armour.factor"); percent(rules.armour.cap, "armour.cap")
        positive(rules.evasion.base, "evasion.base"); percent(rules.evasion.cap, "evasion.cap")
        if (rules.evasion.perLevel < 0 || rules.stun.share < 0 || rules.stun.duration < 0) throw CampaignExceptions.funExceptionContent(method, "stun")
        if (rules.shield.rechargeDelay < 0 || rules.shield.rechargePerSecond < 0) throw CampaignExceptions.funExceptionContent(method, "shield")
        if (rules.retreat.delay < 0) throw CampaignExceptions.funExceptionContent(method, "retreat.delay")
        if (rules.death.fromLevel < 1) throw CampaignExceptions.funExceptionContent(method, "death.fromLevel")
        percent(rules.death.experienceShare, "death.experienceShare")
        rules.ailments.forEach { rule ->
            if (rule.ailment !in ailments) throw CampaignExceptions.funExceptionContent(method, "ailment ${rule.ailment}")
            if (rule.type !in damage) throw CampaignExceptions.funExceptionContent(method, "ailment ${rule.ailment} by ${rule.type}")
            percent(rule.chance, "ailment ${rule.ailment} chance"); percent(rule.threshold, "ailment ${rule.ailment} threshold")
            rule.heroChance?.let { percent(it, "ailment ${rule.ailment} heroChance") }
            if (rule.magnitude < 0 || rule.duration <= 0) throw CampaignExceptions.funExceptionContent(method, "ailment ${rule.ailment}")
        }
        if (rules.ailments.map { it.ailment }.toSet().size != rules.ailments.size) throw CampaignExceptions.funExceptionContent(method, "ailments")
    }
}
