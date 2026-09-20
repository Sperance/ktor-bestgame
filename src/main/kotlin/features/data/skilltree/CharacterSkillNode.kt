package features.data.skilltree

import application.enums.EnumSkillNodeType
import base.entity.VersionedEntity
import extensions.now
import features.logic.modifiers.Modifier
import features.logic.skilltree.SkillTreeNode
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Узел дерева навыков, взятый конкретным персонажем.
 *
 * Отдельная коллекция Mongo `CharacterSkillNode`: один взятый узел =
 * один документ. Бонусы хранятся здесь снимком, а не ссылкой на дерево:
 * если дерево потом перебалансируют, у уже прокачанных персонажей
 * останется то, что они брали, и изменение затронет только новых.
 */
@Serializable
data class CharacterSkillNode(

    /**
     * Владелец - ссылка на `Character._id`.
     */
    var characterId: String,

    /**
     * Код узла в дереве - ссылка на [SkillTreeNode.code].
     */
    var nodeCode: String,

    /**
     * Снимок бонусов узла на момент взятия.
     */
    var params: MutableList<Modifier> = mutableListOf(),

    /**
     * Снимок названия узла: дерево могло переименовать его уже после взятия.
     */
    var name: String = "",

    /**
     * Снимок вида узла.
     */
    var type: EnumSkillNodeType = EnumSkillNodeType.SMALL,

    /**
     * Снимок стоимости: именно столько очков персонаж за узел заплатил
     * и именно столько вернёт при откате.
     */
    var cost: Int = 1,

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity {

    companion object {
        /**
         * Снимает узел дерева таким, какой он сейчас, и закрепляет за персонажем.
         */
        fun fromNode(characterId: String, node: SkillTreeNode): CharacterSkillNode =
            CharacterSkillNode(
                characterId = characterId,
                nodeCode = node.code,
                params = node.params.toMutableList(),
                name = node.name,
                type = node.type,
                cost = node.cost
            )
    }
}

/**
 * Состояние дерева навыков персонажа.
 *
 * @property total сколько очков персонажу доступно на его уровне
 * @property spent сколько уже потрачено
 */
@Serializable
data class CharacterSkillTreeState(
    val characterId: String,
    val total: Int,
    val spent: Int,
    val available: Int,
    val nodes: List<CharacterSkillNode>,
)
