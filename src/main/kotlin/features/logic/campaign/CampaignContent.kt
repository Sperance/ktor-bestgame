package features.logic.campaign

import features.logic.modifiers.ModifierDefinition
import features.logic.modifiers.ModifierTier
import config.ModifierSeeder
import application.enums.EnumMonsterRarity
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
import java.util.concurrent.atomic.AtomicReference
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.pow


/**
 * Где монстр стоит в бою (с 0.61.0): ближний - в первом ряду, дальний - во втором и бьёт героя с
 * первой секунды. Героя с оружием ближнего боя второй ряд не подпускает, пока жив первый.
 */
enum class EnumMonsterRange { MELEE, RANGED }

/** Что может выпасть: сфера из справочника валюты или экипировка уровня карты. */
enum class EnumLootKind { ORB, EQUIPMENT }

/**
 * Одно изменение характеристики монстра: те же операции, что у модификаторов предметов.
 * С 0.66.0 - диапазон [value]..[max] тира, открытого на уровне карты: само значение внутри него
 * бросает клиент, когда катает монстра.
 */
@Serializable
data class MonsterEffect(val stat: String, val operation: EnumModifierOperation, val value: Double, val max: Double = value)

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
 * Модификатор монстра, каким его видит клиент: описание источника MONSTER из `modifiers.json`
 * (с 0.66.0), пул карты уже разрешён, [weight] - вес в нём, [tier] - лучший тир, открытый на уровне
 * карты, и его диапазоны в [effects]. [minRarity] - с какой редкости монстра он открыт: у редкого
 * монстра пул шире, чем у магического, а `UNIQUE` носит только босс.
 */
@Serializable
data class MonsterModifier(
    val code: String,
    val weight: Int,
    val minLevel: Int = 1,
    val minRarity: EnumMonsterRarity = EnumMonsterRarity.MAGIC,
    val effects: List<MonsterEffect>,
    val tier: Int = 1,
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
 * «Волк-одиночка» (с 0.62.0): герой, который дерётся без отряда, наносит на [dealt] процентов
 * больше любого урона и получает на [taken] процентов меньше. Со вторым бойцом в отряде бонус пропадает.
 */
@Serializable data class LoneWolfRule(val dealt: Double = 10.0, val taken: Double = 10.0)

/**
 * Правила боя - числа, по которым клиент считает автобой (с 0.28.0).
 *
 * Бой остаётся клиентским по решению владельца, но формулы и константы - сервера: клиент читает
 * их вместе с главами и не держит своих. Лимита времени у боя нет с 0.65.0.
 * [variance] - разброс урона удара в процентах; [resistCap], [blockCap] - потолки в процентах.
 * С 0.36.0: [resistHardCap] - выше него не поднимет никакой «+% к максимуму сопротивления»,
 * [ailmentDurationCap] - сильнее этого не сократить длительность состояния на себе.
 */
@Serializable
data class CombatRules(
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
    val loneWolf: LoneWolfRule = LoneWolfRule(),
    /** Мана героя и монстров (с 0.69.0). */
    val mana: ManaRule = ManaRule(),
    /** Фляги (с 0.69.0): заряды за убийство по редкости монстра. */
    val flasks: FlaskRule = FlaskRule(),
)

/** Мана (с 0.69.0): регенерация - столько процентов максимума в секунду. */
@Serializable
data class ManaRule(val regen: Double = 2.0)

/** Фляги (с 0.69.0): заряды за убийство монстра по его редкости; на входе в зону фляги полные. */
@Serializable
data class FlaskRule(val perKill: Map<EnumMonsterRarity, Double> = mapOf(
    EnumMonsterRarity.NORMAL to 1.0, EnumMonsterRarity.MAGIC to 2.0, EnumMonsterRarity.RARE to 3.0, EnumMonsterRarity.UNIQUE to 5.0))

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
    /** Умения босса (с 0.69.0): одно-два из книги умений монстров. */
    val skills: List<String> = emptyList(),
)

/**
 * Боссы (с 0.32.0): страж выхода каждой карты. Убитый, он возвращается через [respawnHours] часов;
 * до тех пор выход открыт. С него падает добыча его таблицы с редкостью `UNIQUE`, с шансом
 * [uniqueChance] - уникалка из [uniquePools], с шансом [ownUniqueChance] - из собственных пулов босса.
 */
/**
 * Услуги карты за золото (с 0.34.0): «Вызов стража» - убитый босс снова у выхода, за
 * [summonPerLevel] × уровень карты. «Карта сокровищ» снята в 0.64.0.
 */
@Serializable
data class ServiceRule(val summonPerLevel: Long)

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
 * его редкости и героя), с босса - [bossChance]; с шансом [nextChance] это карта одной из зон, куда
 * ведут связи из той, где она упала (0.67.0). Редкость упавшей карты - по весам [rarities]. [risk] - сколько процентов к количеству,
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

/**
 * Правило боссов. С 0.66.0 у боссов свой пул модификаторов [modifierPools]: сигнатурные строки босса
 * берутся из него на тире уровня `карта + tierReach`, и ещё от `rolls[0]` до `rolls[1]` случайных
 * строк того же пула на тире карты катает клиент при каждой встрече.
 */
@Serializable
data class BossRule(val respawnHours: Double, val uniqueChance: Double, val ownUniqueChance: Double, val behaviour: BehaviourRule,
                    val uniquePools: List<String> = emptyList(), val modifierPools: List<String> = listOf("boss"),
                    val rolls: List<Int> = listOf(1, 2), val tierReach: Int = 5)

/**
 * Босс карты, какой его видит клиент: характеристики и сигнатурные [modifiers] уже подняты до уровня
 * карты; [pool] - строки его пула на тире карты, из которых клиент добирает от `rolls[0]` до
 * `rolls[1]` случайных при каждой встрече (0.66.0).
 */
@Serializable
data class CampaignBoss(val code: String, val form: String, val stats: Map<String, Double>, val behaviour: BehaviourRule, val modifiers: List<MonsterModifier>,
                        val range: EnumMonsterRange = EnumMonsterRange.MELEE, val pool: List<MonsterModifier> = emptyList(), val rolls: List<Int> = listOf(0, 0),
                        /** Умения босса (с 0.69.0) - коды из книги умений монстров; колдует он за свою ману. */
                        val skills: List<String> = emptyList())

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

/** Точка карты мира (с 0.67.0): `x` вправо, `y` вверх от старта, в единицах мира [WorldRule]. */
@Serializable
data class WorldPoint(val x: Int, val y: Int)

/**
 * Карта мира (с 0.67.0): поле [width] на [height] единиц, по которому разложены жетоны зон. Мир
 * растёт вверх - новые регионы ложатся выше, и координаты прежних жетонов не сдвигаются.
 */
@Serializable
data class WorldRule(val width: Int, val height: Int)

/**
 * Рост монстров со «спадом» (с 0.67.0): до уровня [from] каждая характеристика `growth` растёт
 * полной степенью за уровень, выше - лишь долей [rate] этой степени. Без спада уровень 70 давал бы
 * монстрам здоровья в тысячи раз больше, чем снаряжение даёт герою. С 0.68.0 тем же спадом растёт
 * и золото с монстров: иначе к семидесятой карте его выпадало бы в семьсот раз больше, чем на первой.
 */
@Serializable
data class GrowthTaper(val from: Int = Int.MAX_VALUE, val rate: Double = 1.0) {
    /** Сколько полных степеней роста набрано к уровню [level]: по одной до [from], по [rate] выше. */
    fun steps(level: Int): Double = (minOf(level, from) - 1) + rate * maxOf(0, level - from)
}

/**
 * Зона карты мира - её жетон (с 0.67.0; раньше карта главы). [x] и [y] - место жетона на карте
 * мира, [from] - зоны, из которых сюда ведут связи: открыта зона, когда пройдена хоть одна из них,
 * а стартовая, без связей, открыта всегда. [finale] - финал региона: только из него связи ведут в
 * следующий регион.
 */
@Serializable
data class CampaignMapTemplate(
    val code: String,
    val biome: String,
    val level: Int,
    val x: Int,
    val y: Int,
    val from: List<String> = emptyList(),
    val finale: Boolean = false,
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

/** Регион карты мира (с 0.67.0; раньше глава): его зоны и место подписи [label] на карте. */
@Serializable
data class CampaignRegionTemplate(val code: String, val label: WorldPoint, val zones: List<CampaignMapTemplate>)

@Serializable
data class CampaignContentFile(
    val defaults: Map<String, Double> = emptyMap(),
    val growth: Map<String, Double> = emptyMap(),
    val growthTaper: GrowthTaper = GrowthTaper(),
    val rarities: List<CampaignRarity>,
    val lootTables: Map<String, LootTable>,
    val monsters: List<MonsterTemplate>,
    val world: WorldRule,
    val regions: List<CampaignRegionTemplate>,
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
    /** Все зоны мира - регион за регионом, в порядке файла. */
    val zones: List<CampaignMapTemplate> get() = regions.flatMap { it.zones }

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
 * Зона карты мира, какой её видит клиент (с 0.67.0 - жетон региона [region] в точке [x], [y]).
 * [from] - откуда в неё ведут связи, [to] - куда ведут из неё; зона без [to], не [finale], - тупик.
 *
 * Модификаторы тоже подняты до уровня карты: «+3 к урону огнём» на первой карте и на
 * двадцатой - разные числа, и считать рост второй раз на клиенте было бы второй копией правила.
 */
@Serializable
data class CampaignMap(
    val code: String,
    val region: String,
    val x: Int,
    val y: Int,
    val from: List<String>,
    val to: List<String>,
    val finale: Boolean = false,
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
    /** Модификаторы стражей кристаллов (с 0.69.0) на уровне карты: по одному на вид эссенции. */
    val essences: List<MonsterModifier> = emptyList(),
) {
    /**
     * Жетон зоны для карты мира (0.68.1): зона без пулов модификаторов - своих, босса и стража порчи.
     * Пулы у всех зон одни, отличаются лишь тиром уровня, и сто зон с ними весили бы мегабайты; целиком
     * зона приходит ответом на вход в неё, [MapLaunch.zone].
     */
    fun token(): CampaignMap = copy(modifiers = emptyList(), boss = boss.copy(pool = emptyList()), corrupted = corrupted.copy(pool = emptyList()), essences = emptyList())
}

/** Регион карты мира, каким его видит клиент (с 0.67.0; раньше глава). */
@Serializable
data class CampaignRegion(val code: String, val label: WorldPoint, val zones: List<CampaignMap>)

/** Карта мира, правила редкости и правила боя - всё, что клиенту нужно, чтобы драться, одним ответом. */
@Serializable
data class CampaignView(
    val world: WorldRule,
    val regions: List<CampaignRegion>,
    val rarities: List<CampaignRarity>,
    val combat: CombatRules,
    val services: ServiceRule,
    val maps: MapRule,
    val fountains: FountainRule = FountainRule(),
    val corruption: CorruptionRule = CorruptionRule(),
    val vaal: VaalRule = VaalRule(),
)

/**
 * Содержимое кампании - карта мира с регионами и зонами, монстры, их модификаторы и добыча.
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

    /** Коды всех зон мира. */
    val mapCodes: Set<String> by lazy { file.zones.mapTo(mutableSetOf()) { it.code } }

    /** Связи зон карты мира. */
    val graph: WorldGraph by lazy { WorldGraph(file.zones) }

    private val templates: Map<String, CampaignMapTemplate> by lazy { file.zones.associateBy { it.code } }

    /** Шаблон зоны по коду - таблицы сундуков, пулы; null для неизвестного кода. */
    fun template(code: String): CampaignMapTemplate? = templates[code]

    private class Resolved(val pools: PoolTable, val modifiers: List<ModifierDefinition>, whole: CampaignView) {
        /** Зоны целиком - для входа и проверок. */
        val maps: Map<String, CampaignMap> = whole.regions.flatMap { it.zones }.associateBy { it.code }
        /** Карта мира жетонами, без пулов модификаторов (0.68.1). */
        val view: CampaignView = whole.copy(regions = whole.regions.map { region -> region.copy(zones = region.zones.map(CampaignMap::token)) })
    }

    private val resolved = AtomicReference<Resolved?>(null)

    /**
     * Кампания, как её видит клиент: пулы модификаторов монстров разрешены по таблице [pools], а сами
     * модификаторы (0.66.0) - описания источника MONSTER из [modifiers]. Таблица и список у кешей одни
     * на ревизию, поэтому вид пересобирается, лишь когда пулы или описания правили. Зоны в нём - жетоны
     * без пулов (0.68.1), зона целиком - [map].
     */
    fun view(pools: PoolTable, modifiers: List<ModifierDefinition>): CampaignView = resolvedFor(pools, modifiers).view

    /** Карта кампании по коду в том же виде. */
    fun map(code: String, pools: PoolTable, modifiers: List<ModifierDefinition>): CampaignMap? = resolvedFor(pools, modifiers).maps[code]

    private fun resolvedFor(pools: PoolTable, modifiers: List<ModifierDefinition>): Resolved =
        resolved.get()?.takeIf { it.pools === pools && it.modifiers === modifiers }
            ?: Resolved(pools, modifiers, resolve(file, pools, modifiers)).also(resolved::set)

    /** Описания модификаторов монстров из файла модификаторов - для проверок и тестов без базы. */
    val seededModifiers: List<ModifierDefinition> by lazy { ModifierSeeder.seedDefinitions().filter { it.isMonster() } }

    val monsters: Map<String, MonsterTemplate> by lazy { file.monsters.associateBy { it.code } }

    fun load(text: String): CampaignContentFile = json.decodeFromString(CampaignContentFile.serializer(), text).also(::validate)

    /** Открытые зоны мира: стартовая и те, у кого пройдена хоть одна зона, ведущая к ним (0.67.0). */
    fun unlocked(passed: Collection<String>): List<String> = graph.unlocked(passed)

    fun resolve(content: CampaignContentFile, pools: PoolTable, modifiers: List<ModifierDefinition>): CampaignView {
        val monsters = content.monsters.associateBy { it.code }
        val monsterModifiers = modifiers.filter { it.isMonster() }
        val graph = WorldGraph(content.zones)
        val regions = content.regions.map { region ->
            CampaignRegion(region.code, region.label, region.zones.map { map ->
                CampaignMap(
                    code = map.code,
                    region = region.code,
                    x = map.x,
                    y = map.y,
                    from = map.from,
                    to = graph.next(map.code),
                    finale = map.finale,
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
                    modifiers = pools.of(monsterModifiers, map.modifierPools).map { (modifier, weight) -> raise(content, modifier, weight, map.level) },
                    boss = guardian(content, pools, monsterModifiers, monsters, map.boss, map, content.bosses.behaviour),
                    corrupted = guardian(content, pools, monsterModifiers, monsters, map.corrupted, map, content.bosses.behaviour),
                    essences = pools.of(monsterModifiers, features.logic.essences.EssenceContent.book.crystals.modifierPools)
                        .map { (modifier, weight) -> raise(content, modifier, weight, map.level) },
                )
            })
        }
        // Тир редкости разворачивается в «больше» к каждой растущей характеристике.
        val rarities = content.rarities.map { rarity ->
            if (rarity.statScale <= 0) rarity
            else rarity.copy(effects = rarity.effects + content.growth.keys.map { MonsterEffect(it, EnumModifierOperation.MORE, rarity.statScale) })
        }
        return CampaignView(content.world, regions, rarities, content.combat, content.services, content.maps, content.fountains, content.corruption, content.vaal)
    }

    /**
     * Босс или страж осквернённой зоны: та же форма ответа, характеристики подняты до уровня карты.
     * Сигнатурные строки босса - на тире уровня `карта + tierReach`, его пул - на тире карты (0.66.0).
     */
    private fun guardian(content: CampaignContentFile, pools: PoolTable, modifiers: List<ModifierDefinition>, monsters: Map<String, MonsterTemplate>,
                         code: String, map: CampaignMapTemplate, defaultBehaviour: BehaviourRule): CampaignBoss =
        monsters.getValue(code).let { boss ->
            val rule = content.bosses
            val byCode = modifiers.associateBy { it.code }
            val pool = pools.of(modifiers, rule.modifierPools)
            CampaignBoss(boss.code, boss.form, (content.defaults + boss.stats).mapValues { (stat, value) -> scale(content, stat, value, map.level) },
                boss.behaviour ?: defaultBehaviour,
                boss.modifiers.map { modCode -> raise(content, byCode.getValue(modCode), pools.weight(modCode, rule.modifierPools), map.level, map.level + rule.tierReach) },
                content.range(boss),
                pool = pool.filter { (modifier) -> modifier.code !in boss.modifiers }.map { (modifier, weight) -> raise(content, modifier, weight, map.level) },
                rolls = rule.rolls, skills = boss.skills)
        }

    /**
     * Модификатор монстра на уровне карты: лучший тир, открытый на [tierLevel] (обычно сам уровень
     * карты), а его плоские прибавки к растущим характеристикам подняты по `growth`, как у монстра.
     */
    private fun raise(content: CampaignContentFile, modifier: ModifierDefinition, weight: Int, level: Int, tierLevel: Int = level): MonsterModifier {
        val (number, tier) = modifier.bestTierAt(tierLevel) ?: (1 to ModifierTier(1, modifier.effects.map { listOf(0.0, 0.0) }))
        return MonsterModifier(modifier.code, weight, modifier.tiers.minOf { it.level }, modifier.minRarity ?: EnumMonsterRarity.MAGIC,
            modifier.effects.mapIndexed { index, effect ->
                val stat = (effect.stat as Enum<*>).name
                val (min, max) = tier.values[index]
                if (effect.operation == EnumModifierOperation.ADD) MonsterEffect(stat, effect.operation, scale(content, stat, min, level), scale(content, stat, max, level))
                else MonsterEffect(stat, effect.operation, min, max)
            }, number)
    }

    /**
     * Характеристика на уровне карты: растёт только то, что названо в `growth`, и растёт степенью,
     * а выше [GrowthTaper.from] (0.67.0) - лишь долей этой степени за каждый уровень.
     */
    fun scale(content: CampaignContentFile, stat: String, value: Double, level: Int): Double {
        val factor = content.growth[stat] ?: return value
        return Math.round(value * factor.pow(content.growthTaper.steps(level)) * 100.0) / 100.0
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
        val monsterModifiers = seededModifiers
        monsterModifiers.forEach { modifier ->
            if (modifier.minRarity == EnumMonsterRarity.NORMAL) throw CampaignExceptions.funExceptionContent(method, "modifier ${modifier.code} on a normal monster")
        }
        // У каждой редкости с модификаторами на каждой карте должно быть из чего выбирать - по пулам из файла.
        val monsterPools = PoolSeeder.table(EnumPoolTarget.MONSTER)
        content.zones.forEach { map ->
            val pool = monsterPools.of(monsterModifiers, map.modifierPools).map { it.value }
            content.rarities.filter { it.modifiers[1] > 0 }.forEach { rarity ->
                if (pool.count { (it.minRarity ?: EnumMonsterRarity.MAGIC) <= rarity.rarity } < rarity.modifiers[1]) throw CampaignExceptions.funExceptionContent(method, "pool of ${rarity.rarity} on ${map.code}")
            }
        }
        content.bosses.let { rule ->
            if (rule.rolls.size != 2 || rule.rolls[0] < 0 || rule.rolls[0] > rule.rolls[1] || rule.tierReach < 0 || rule.modifierPools.isEmpty())
                throw CampaignExceptions.funExceptionContent(method, "bosses rolls")
            val pool = monsterPools.of(monsterModifiers, rule.modifierPools)
            if (pool.size < rule.rolls[1]) throw CampaignExceptions.funExceptionContent(method, "boss pool")
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
            monster.skills.forEach { if (it !in features.logic.skills.SkillContent.monsterSkills) throw CampaignExceptions.funExceptionContent(method, "skill $it of ${monster.code}") }
            if (monster.skills.isNotEmpty() && (monster.stats["STOCK_MANA"] ?: 0.0) <= 0) throw CampaignExceptions.funExceptionContent(method, "skilled ${monster.code} without mana")
        }
        content.monsters.forEach { monster ->
            // Колдующий монстр без маны никогда бы не колдовал - это ошибка файла, а не тихий монстр.
            if ((monster.stats["STOCK_ATTACK_MAGICAL"] ?: 0.0) > 0 && (monster.stats["STOCK_MANA"] ?: 0.0) <= 0)
                throw CampaignExceptions.funExceptionContent(method, "caster ${monster.code} without mana")
        }
        val monsters = content.monsters.map { it.code }.toSet()
        if (monsters.size != content.monsters.size) throw CampaignExceptions.funExceptionContent(method, "monster codes")
        validate(content.combat)
        val codes = content.zones.map { it.code }
        if (codes.toSet().size != codes.size) throw CampaignExceptions.funExceptionContent(method, "map codes")
        validateWorld(content)
        content.zones.forEach { map ->
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
        val modifierCodes = monsterPools.of(monsterModifiers, content.bosses.modifierPools).map { it.value.code }.toSet()
        // Собственные уникалки (0.67.0) - обязательно только у боссов финалов регионов; прочие роняют мировые.
        val finaleBosses = content.zones.filter { it.finale }.map { it.boss }.toSet()
        content.monsters.filter { it.boss }.forEach { boss ->
            if ((boss.code in finaleBosses && boss.uniquePools.isEmpty()) || boss.modifiers.isEmpty() || boss.modifiers.any { it !in modifierCodes })
                throw CampaignExceptions.funExceptionContent(method, "boss ${boss.code}")
        }
        val bosses = content.zones.map { it.boss }
        if (bosses.toSet().size != bosses.size) throw CampaignExceptions.funExceptionContent(method, "a boss guards two zones")
        content.monsters.filter { it.corrupted }.forEach { guardian ->
            if (guardian.boss) throw CampaignExceptions.funExceptionContent(method, "corrupted boss ${guardian.code}")
            if (guardian.uniquePools.isNotEmpty() || guardian.modifiers.any { it !in modifierCodes }) throw CampaignExceptions.funExceptionContent(method, "corrupted ${guardian.code}")
        }
        content.bosses.let { rule ->
            if (rule.respawnHours <= 0 || rule.uniqueChance !in 0.0..1.0 || rule.ownUniqueChance !in 0.0..1.0 || rule.uniquePools.isEmpty()) throw CampaignExceptions.funExceptionContent(method, "bosses")
        }
        if (content.services.summonPerLevel <= 0) throw CampaignExceptions.funExceptionContent(method, "services")
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

    /**
     * Карта мира (0.67.0): один старт в первом регионе, связи только от младшей зоны к старшей и
     * только внутри региона или из его финала в следующий, у каждого региона ровно один финал на его
     * вершине, жетоны и подписи - в поле мира, и до каждой зоны можно дойти от старта.
     */
    private fun validateWorld(content: CampaignContentFile) {
        val method = "CampaignWorld"
        fun fail(what: String): Nothing = throw CampaignExceptions.funExceptionContent(method, what)
        val world = content.world
        if (world.width <= 0 || world.height <= 0) fail("world size")
        fun inside(x: Int, y: Int) = x in 0..world.width && y in 0..world.height
        if (content.regions.isEmpty()) fail("regions")
        val index = content.regions.withIndex().associate { (i, region) -> region.code to i }
        if (index.size != content.regions.size) fail("region codes")
        val regionOf = content.regions.flatMap { region -> region.zones.map { it.code to index.getValue(region.code) } }.toMap()
        val zones = content.zones.associateBy { it.code }
        val starts = content.zones.filter { it.from.isEmpty() }
        if (starts.size != 1 || regionOf.getValue(starts[0].code) != 0) fail("one start in the first region")
        content.regions.forEach { region ->
            if (region.zones.isEmpty()) fail("zones of ${region.code}")
            if (!inside(region.label.x, region.label.y)) fail("label of ${region.code}")
            val finale = region.zones.filter { it.finale }
            if (finale.size != 1 || finale[0].level != region.zones.maxOf { it.level }) fail("finale of ${region.code}")
        }
        content.zones.forEach { zone ->
            if (!inside(zone.x, zone.y)) fail("place of ${zone.code}")
            if (zone.from.toSet().size != zone.from.size) fail("links of ${zone.code}")
            zone.from.forEach { code ->
                val source = zones[code] ?: fail("link $code of ${zone.code}")
                if (source.level >= zone.level) fail("link $code of ${zone.code} goes down")
                val step = regionOf.getValue(zone.code) - regionOf.getValue(code)
                if (step != 0 && !(step == 1 && source.finale)) fail("link $code of ${zone.code} skips a region")
            }
        }
        WorldGraph(content.zones).unreachable().takeIf { it.isNotEmpty() }?.let { fail("unreachable $it") }
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
        percent(rules.variance, "variance")
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
