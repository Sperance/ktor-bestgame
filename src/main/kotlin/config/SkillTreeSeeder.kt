package config

import application.enums.EnumSkillNodeType
import base.exception.model.LocaleExceptions
import base.exception.model.ModifierExceptions
import base.exception.model.SkillTreeExceptions
import extensions.toStableObjectId
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierDefinition
import features.logic.skilltree.SkillTreeNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Начальные данные коллекции `SkillTreeNode` - дерево навыков в духе POE.
 *
 * Само дерево лежит данными в `resources/skilltree/tree.json`: три сотни узлов
 * с координатами, связями и бонусами. Здесь только чтение файла и превращение
 * его в документы - форму дерева правят в файле, а не в коде.
 *
 * Шесть классовых областей стоят по кругу, Скион - в центре. Из каждой области
 * ветки расходятся наружу (колёса вокруг нотаблей, тропы, тупики с кейстоунами
 * и гнёзда под самоцветы) и две ветки уходят внутрь, к центру. Именно там
 * соседние области смыкаются: общего кольца безликих узлов больше нет, в чужую
 * часть дерева попадают через собственные узлы соседа, потому что стартовый
 * узел чужого класса взять нельзя.
 *
 * Узлов заметно больше, чем очков у сотого уровня, поэтому взять всё нельзя -
 * как и в POE, приходится выбирать.
 */
object SkillTreeSeeder {

    /**
     * Папка с деревом внутри ресурсов.
     */
    const val FOLDER = "skilltree"

    /**
     * Файл с узлами.
     */
    const val FILE = "tree.json"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Бонус узла в файле: код описания модификатора и по значению на каждый его эффект.
     */
    @Serializable
    private data class NodeBonus(val code: String, val values: List<Double> = emptyList())

    /**
     * Узел в том виде, в каком он лежит в файле.
     */
    @Serializable
    private data class NodeRecord(
        val code: String,
        val type: EnumSkillNodeType,
        val cost: Int = 1,
        val positionX: Int = 0,
        val positionY: Int = 0,
        val connections: List<String> = emptyList(),
        val params: List<NodeBonus> = emptyList(),
        val options: List<List<NodeBonus>> = emptyList(),
    )

    @Serializable
    private data class TreeDocument(val nodes: List<NodeRecord> = emptyList())

    /**
     * Документы коллекции `SkillTreeNode`.
     *
     * Проверки стоят здесь, а не только в тесте, потому что дерево пришло из файла:
     * опечатка в коде соседа или в коде модификатора должна остановить старт, а не
     * оставить висящую связь, которую заметят через неделю по жалобе игрока.
     *
     * @param definitions описания модификаторов - из них берутся _id бонусов
     */
    fun seed(definitions: List<ModifierDefinition>): List<SkillTreeNode> {
        val byCode = definitions.associateBy { it.code }
        val records = json.decodeFromString(TreeDocument.serializer(), resource(FILE)).nodes

        val duplicates = records.groupBy { it.code }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty())
            throw SkillTreeExceptions.funException("seed", "Duplicate node codes: $duplicates")

        val known = records.mapTo(mutableSetOf()) { it.code }
        records.forEach { record ->
            record.connections.forEach { neighbour ->
                if (neighbour !in known)
                    throw SkillTreeExceptions.funException("seed", "${record.code} is linked to unknown $neighbour")
            }
        }

        val types = records.associate { it.code to it.type }
        records.forEach { record ->
            val choosing = record.type == EnumSkillNodeType.MASTERY || record.type == EnumSkillNodeType.ATTRIBUTE
            if (choosing != record.options.isNotEmpty())
                throw SkillTreeExceptions.funException("seed", "${record.code}: only a mastery or an attribute node offers options")
            // Мастерство - лист кластера: его соседи только notable, иначе через него шёл бы путь
            if (record.type == EnumSkillNodeType.MASTERY && record.connections.any { types[it] != EnumSkillNodeType.NOTABLE })
                throw SkillTreeExceptions.funException("seed", "${record.code}: a mastery links to notables only")
        }

        fun bonus(bonus: NodeBonus): Modifier {
            val definition = byCode[bonus.code] ?: throw ModifierExceptions.funExceptionCodeNotFound("seed", bonus.code)
            return Modifier.passive(definition._id, bonus.values)
        }

        return records.map { record ->
            SkillTreeNode(
                code = record.code,
                type = record.type,
                params = record.params.mapTo(mutableListOf(), ::bonus),
                connections = record.connections.toMutableList(),
                options = record.options.map { option -> option.map(::bonus) },
                // Стартовый узел класса персонаж получает при создании и не платит за него;
                // гнездо стоит очко, как обычный узел - место на дереве само по себе ценно.
                cost = if (record.type == EnumSkillNodeType.START) 0 else record.cost,
                positionX = record.positionX,
                positionY = record.positionY,
                // Дерево пересевается на каждом старте: _id должен быть стабильным
                _id = record.code.toStableObjectId()
            )
        }
    }

    private fun resource(name: String): String =
        javaClass.classLoader.getResourceAsStream("$FOLDER/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: throw LocaleExceptions.funExceptionFileNotFound("resource", "$FOLDER/$name")
}
