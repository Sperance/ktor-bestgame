package features.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.exception.BadRequestException
import base.exception.ConflictException
import base.exception.NotFoundException
import base.service.BaseService
import features.characters.CharacterRepository
import features.modifier.ItemGenerationService
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class EquipmentService(
    private val equipRepo: EquipmentRepository = EquipmentRepository(),
    private val charRepo: CharacterRepository = CharacterRepository()
) : BaseService<Equipment, EquipmentTable>(equipRepo, Equipment.serializer()) {

    override fun validateCreate(entity: Equipment) {
        if (!charRepo.exists(entity.characterId)) {
            throw NotFoundException("Character(id=${entity.characterId}) not found")
        }
        if (entity.slot == EnumEquipmentType.UNDEFINED) {
            throw BadRequestException("Field 'slot' must not be UNDEFINED")
        }
    }

    override fun validateUpdate(id: Long, entity: Equipment) {
        if (entity.equippedSlot == EnumEquipmentType.UNDEFINED) {
            throw BadRequestException("Field 'equippedSlot' must not be UNDEFINED")
        }
    }

    // ==================== Generation ====================

    /**
     * Сгенерировать предмет (PoE-стиль) и сохранить в БД.
     *
     * @param characterId  Владелец предмета
     * @param slot         Слот экипировки
     * @param itemLevel    Уровень предмета (1..100)
     * @param rarity       Редкость
     * @return Сохранённый предмет
     */
    fun generateAndSave(
        characterId: Long,
        slot: EnumEquipmentType,
        itemLevel: Int,
        rarity: EnumRarity
    ): Equipment {
        if (!charRepo.exists(characterId)) {
            throw NotFoundException("Character(id=$characterId) not found")
        }
        if (slot == EnumEquipmentType.UNDEFINED) {
            throw BadRequestException("Slot must not be UNDEFINED")
        }
        val itemLevel = itemLevel.coerceIn(1, 100)

        val generated = ItemGenerationService.generate(
            slot = slot,
            itemLevel = itemLevel,
            rarity = rarity,
            characterId = characterId
        )

        val json = Equipment.serializer().let {
            kotlinx.serialization.json.Json.encodeToJsonElement(it, generated)
        } as JsonObject

        return equipRepo.create(json)
    }

    // ==================== Inventory queries ====================

    fun findByCharacter(characterId: Long) = equipRepo.findByCharacter(characterId)

    fun findEquipped(characterId: Long) = equipRepo.findEquipped(characterId)

    fun findInBag(characterId: Long) = equipRepo.findInBag(characterId)

    // ==================== Equip / Unequip ====================

    /**
     * Надеть предмет в слот.
     *
     * 1. Проверяем, что предмет принадлежит персонажу
     * 2. Предмет должен быть в сумке (equippedSlot == null)
     * 3. Слот предмета совпадает с item.slot
     * 4. Если слот занят — сначала снять старый предмет
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

        val previousItem = equipRepo.findBySlot(characterId, item.slot)
        if (previousItem != null) unequipInternal(previousItem)

        val equipped = equipInternal(item)
        val unequipped = previousItem?.let { equipRepo.findById(it.id) }
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
        val json = jsonOf("equippedSlot" to item.slot.name, "version" to item.version)
        return equipRepo.update(item.id, json)
    }

    private fun unequipInternal(item: Equipment) {
        val json = jsonOfWithNull("version" to item.version, nullField = "equippedSlot")
        equipRepo.update(item.id, json)
    }

    private fun jsonOf(vararg pairs: Pair<String, Any>): JsonObject =
        JsonObject(pairs.associate { (k, v) ->
            k to when (v) {
                is String  -> JsonPrimitive(v)
                is Long    -> JsonPrimitive(v)
                is Int     -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                else       -> JsonPrimitive(v.toString())
            }
        })

    private fun jsonOfWithNull(vararg pairs: Pair<String, Any>, nullField: String): JsonObject {
        val map = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        for ((k, v) in pairs) {
            map[k] = when (v) {
                is String  -> JsonPrimitive(v)
                is Long    -> JsonPrimitive(v)
                is Int     -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                else       -> JsonPrimitive(v.toString())
            }
        }
        map[nullField] = JsonNull
        return JsonObject(map)
    }
}
