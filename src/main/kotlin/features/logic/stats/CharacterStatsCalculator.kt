package features.logic.stats

import application.enums.IntEnumStat
import features.caches.EquipmentCache
import features.data.character.Character
import features.data.character.character_data.CharacterSkillNode
import features.data.inventory.CharacterEquipment
import features.logic.modifiers.ModifierCalculator
import features.logic.modifiers.StatOperation
import features.logic.progression.CharacterClass
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Предмет, который надет, но не работает: его требования не выполнены.
 */
@Serializable
data class InactiveEquipment(
    val inventoryId: String,
    /**
     * Код шаблона предмета - название клиент возьмёт из локализации.
     */
    val code: String,
    val reasons: List<String>,
)

/**
 * Итоговые характеристики персонажа.
 *
 * Что персонажу не по силам надеть, с 0.49.0 сервер не перечисляет: требования лежат на
 * шаблонах, а шаблоны у клиента есть целиком, и вердикт он выносит сам тем же правилом.
 *
 * @property active id работающих предметов инвентаря
 * @property inactive надетые предметы, чьи требования не выполнены
 */
@Serializable
data class CharacterStats(
    val characterId: String,
    val level: Int,
    val stats: Map<IntEnumStat, Double>,
    val active: List<String>,
    val inactive: List<InactiveEquipment>,
)

/**
 * Расчёт характеристик персонажа.
 *
 * Считается в два прохода, потому что требования предметов зависят от
 * атрибутов, а атрибуты - от предметов:
 *
 * 1. база класса на уровне персонажа плюс дерево навыков;
 * 2. экипировка в порядке слотов - каждый следующий предмет проверяется
 *    по характеристикам, которые дали база, дерево и уже признанные
 *    рабочими предметы.
 *
 * Порядок слотов и делает результат детерминированным: при взаимной
 * зависимости двух предметов работать будет тот, чей слот раньше.
 * Лист пересчитывается лениво: только перед проверкой требований и один раз в конце.
 */
object CharacterStatsCalculator : KoinComponent {
    private val equipmentCache: EquipmentCache by inject()

    fun calculate(
        character: Character,
        characterClass: CharacterClass,
        skillNodes: Collection<CharacterSkillNode>,
        equipped: Collection<CharacterEquipment>,
    ): CharacterStats {
        val level = character.level.toInt()
        val base = characterClass.baseOn(level)

        // Проход 1: то, что не зависит от экипировки.
        // Конверсии класса ("+1 к здоровью за каждые 2 Силы") идут здесь же: это
        // постоянные модификаторы персонажа, а не база, поэтому в baseOn их нет.
        // Считает их тот же ModifierCalculator - он сортирует статы по order, так что
        // Сила уже посчитана к моменту, когда до здоровья доходит очередь.
        val operations = ArrayList<StatOperation>(ModifierCalculator.expand(characterClass.params))
        operations += ModifierCalculator.expand(skillNodes.flatMap { it.params })
        var stats = ModifierCalculator.compute(base, operations)
        var stale = false

        // Проход 2: экипировка в порядке слотов
        val active = mutableListOf<String>()
        val inactive = mutableListOf<InactiveEquipment>()
        val takenNodes = skillNodes.mapTo(HashSet()) { it.code }
        equipped.sortedBy { it.equippedSlot?.ordinal ?: Int.MAX_VALUE }.forEach { item ->
            val template = equipmentCache.findById(item.equipmentId) ?: return@forEach
            // Инструмент (0.37.0) работает только в своей профессии, а не на герое; фляга (0.69.0) - только выпитая в бою.
            if (template.slot.isTool || template.slot.isFlask) return@forEach

            // Самоцвет работает, только пока взято гнездо, в котором он сидит:
            // вернули узел - самоцвет остался на месте, но считаться перестал.
            val socket = item.socketCode
            if (socket != null && socket !in takenNodes) {
                inactive.add(InactiveEquipment(item._id, template.code, listOf("socket: need $socket, have none")))
                return@forEach
            }

            if (EquipmentRequirements.demanding(template)) {
                if (stale) { stats = ModifierCalculator.compute(base, operations); stale = false }
                val unmet = EquipmentRequirements.unmet(template, level, stats)
                if (unmet.isNotEmpty()) {
                    inactive.add(InactiveEquipment(item._id, template.code, unmet.map { "${it.name}: need ${it.required}, have ${it.actual}" }))
                    return@forEach
                }
            }

            active.add(item._id)
            // База приходит из шаблона: экземпляр её не хранит, чтобы одно и то же
            // число не лежало в базе данных дважды и перебалансировка доезжала до копий.
            operations += ModifierCalculator.foldItem(template.baseParams + item.params)
            stale = true
        }
        if (stale) stats = ModifierCalculator.compute(base, operations)

        return CharacterStats(character._id, level, stats, active, inactive)
    }
}
