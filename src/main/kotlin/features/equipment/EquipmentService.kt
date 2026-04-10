package features.equipment

import base.exception.BadRequestException
import base.exception.ConflictException
import base.exception.NotFoundException
import base.service.BaseService
import features.characters.CharacterRepository

class EquipmentService(
    private val equipRepo: EquipmentRepository = EquipmentRepository(),
    private val charRepo: CharacterRepository = CharacterRepository()
) : BaseService<Equipment, EquipmentTable>(equipRepo, Equipment.serializer()) {

    override fun entityName() = "Equipment"

    override fun validateCreate(entity: Equipment) {
        if (!charRepo.exists(entity.characterId)) {
            throw NotFoundException("Character(id=${entity.characterId}) not found")
        }
    }

    // ==================== Inventory queries ====================

    fun findByCharacter(characterId: Long) = equipRepo.findByCharacter(characterId)

    fun findEquipped(characterId: Long) = equipRepo.findEquipped(characterId)

    fun findInBag(characterId: Long) = equipRepo.findInBag(characterId)

    // ==================== Equip / Unequip ====================

    /**
     * Надеть предмет в слот.
     *
     * Логика:
     * 1. Проверяем, что предмет существует и принадлежит персонажу
     * 2. Предмет должен быть в сумке (equippedSlot == null)
     * 3. Слот, в который надеваем, совпадает со слотом предмета (item.slot)
     * 4. Если в слоте уже что-то надето — сначала снять
     *
     * @return Пара: (надетый предмет, снятый предмет или null)
     */
    fun equip(characterId: Long, equipmentId: Long): Pair<Equipment, Equipment?> {
        val item = equipRepo.findById(equipmentId)
            ?: throw NotFoundException("Equipment(id=$equipmentId) not found")

        if (item.characterId != characterId) {
            throw BadRequestException("Equipment(id=$equipmentId) does not belong to Character(id=$characterId)")
        }

        if (item.equippedSlot != null) {
            throw ConflictException("Equipment(id=$equipmentId) is already equipped in slot ${item.equippedSlot}")
        }

        // Снимаем текущий предмет из слота (если есть)
        val previousItem = equipRepo.findBySlot(characterId, item.slot)
        if (previousItem != null) {
            unequipInternal(previousItem)
        }

        // Надеваем новый
        val equipped = equipInternal(item)

        val unequipped = previousItem?.let { equipRepo.findById(it.id!!) }
        return equipped to unequipped
    }

    /**
     * Снять предмет (положить в сумку).
     */
    fun unequip(characterId: Long, equipmentId: Long): Equipment {
        val item = equipRepo.findById(equipmentId)
            ?: throw NotFoundException("Equipment(id=$equipmentId) not found")

        if (item.characterId != characterId) {
            throw BadRequestException("Equipment(id=$equipmentId) does not belong to Character(id=$characterId)")
        }

        if (item.equippedSlot == null) {
            throw ConflictException("Equipment(id=$equipmentId) is not equipped")
        }

        unequipInternal(item)
        return equipRepo.findById(equipmentId)!!
    }

    // ==================== Internal ====================

    private fun equipInternal(item: Equipment): Equipment {
        val json = buildJsonObject(
            "equippedSlot" to item.slot.name,
            "version" to item.version
        )
        return equipRepo.update(item.id!!, json)
    }

    private fun unequipInternal(item: Equipment) {
        val json = buildJsonObjectWithNull(
            "version" to item.version,
            nullField = "equippedSlot"
        )
        equipRepo.update(item.id!!, json)
    }

    /** Хелпер: создать JsonObject с простыми полями */
    private fun buildJsonObject(vararg pairs: Pair<String, Any>): kotlinx.serialization.json.JsonObject {
        val map = pairs.associate { (k, v) ->
            k to when (v) {
                is String -> kotlinx.serialization.json.JsonPrimitive(v)
                is Long -> kotlinx.serialization.json.JsonPrimitive(v)
                is Int -> kotlinx.serialization.json.JsonPrimitive(v)
                is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
            }
        }
        return kotlinx.serialization.json.JsonObject(map)
    }

    /** Хелпер: JsonObject с одним nullable-полем (для unequip) */
    private fun buildJsonObjectWithNull(
        vararg pairs: Pair<String, Any>,
        nullField: String
    ): kotlinx.serialization.json.JsonObject {
        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        for ((k, v) in pairs) {
            map[k] = when (v) {
                is String -> kotlinx.serialization.json.JsonPrimitive(v)
                is Long -> kotlinx.serialization.json.JsonPrimitive(v)
                is Int -> kotlinx.serialization.json.JsonPrimitive(v)
                is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
            }
        }
        map[nullField] = kotlinx.serialization.json.JsonNull
        return kotlinx.serialization.json.JsonObject(map)
    }
}
