package features.data.inventory

import application.enums.EnumEquipmentType
import base.entity.VersionedEntity
import extensions.now
import features.data.equipment.equipment_data.Equipment
import features.logic.modifiers.Modifier
import features.logic.modifiers.ModifierRoller
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Экземпляр предмета экипировки в инвентаре персонажа.
 *
 * Отдельная коллекция Mongo `CharacterEquipment`: один предмет
 * в инвентаре = один документ со своими зароленными параметрами.
 *
 * Шаблон предмета ([Equipment]) хранится отдельно и переиспользуется
 * всеми экземплярами, здесь лежит только то, что уникально для копии.
 */
@Serializable
data class CharacterEquipment(

    /**
     * Владелец предмета - ссылка на `Character._id`.
     */
    var characterId: String,

    /**
     * Шаблон предмета - ссылка на [Equipment._id].
     */
    var equipmentId: String,

    /**
     * Зароленные модификаторы конкретно этого экземпляра.
     */
    var params: MutableList<Modifier> = mutableListOf(),

    /**
     * Слот, в котором предмет надет. null - предмет лежит в инвентаре.
     */
    var equippedSlot: EnumEquipmentType? = null,

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity {

    fun isEquipped(): Boolean = equippedSlot != null

    companion object {
        /**
         * Создаёт экземпляр предмета из шаблона с автоматическим роллом модификаторов.
         * Как в POE - при получении предмета сразу роллятся случайные модификаторы и тиры.
         */
        fun fromEquipment(characterId: String, equipment: Equipment): CharacterEquipment =
            CharacterEquipment(
                characterId = characterId,
                equipmentId = equipment._id,
                params = ModifierRoller.roll(equipment)
            )
    }
}
