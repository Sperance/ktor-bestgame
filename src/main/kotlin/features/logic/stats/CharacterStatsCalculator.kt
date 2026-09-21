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

        // Проход 1: то, что не зависит от экипировки
        val treeOperations = ModifierCalculator.expand(skillNodes.flatMap { it.params })
        var stats = ModifierCalculator.compute(base, treeOperations)

        // Проход 2: экипировка в порядке слотов
        val itemOperations = mutableListOf<StatOperation>()
        val active = mutableListOf<String>()
        val inactive = mutableListOf<InactiveEquipment>()

        val takenNodes = skillNodes.mapTo(mutableSetOf()) { it.code }

        equipped.sortedBy { it.equippedSlot?.ordinal ?: Int.MAX_VALUE }.forEach { item ->
            val template = equipmentCache.findById(item.equipmentId) ?: return@forEach

            // Самоцвет работает, только пока взято гнездо, в котором он сидит:
            // вернули узел - самоцвет остался на месте, но считаться перестал.
            val socket = item.socketCode
            if (socket != null && socket !in takenNodes) {
                inactive.add(
                    InactiveEquipment(
                        inventoryId = item._id,
                        code = template.code,
                        reasons = listOf("socket: need $socket, have none")
                    )
                )
                return@forEach
            }

            val unmet = EquipmentRequirements.unmet(template, level, stats)
            if (unmet.isNotEmpty()) {
                inactive.add(
                    InactiveEquipment(
                        inventoryId = item._id,
                        code = template.code,
                        reasons = unmet.map { "${it.name}: need ${it.required}, have ${it.actual}" }
                    )
                )
                return@forEach
            }

            active.add(item._id)
            // База приходит из шаблона: экземпляр её не хранит, чтобы одно и то же
            // число не лежало в базе данных дважды и перебалансировка доезжала до копий.
            itemOperations.addAll(ModifierCalculator.foldItem(template.baseParams + item.params))
            stats = ModifierCalculator.compute(base, treeOperations + itemOperations)
        }

        return CharacterStats(
            characterId = character._id,
            level = level,
            stats = stats,
            active = active,
            inactive = inactive
        )
    }
}
