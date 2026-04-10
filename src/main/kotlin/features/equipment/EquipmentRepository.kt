package features.equipment

import base.repository.BaseRepository
import application.enums.EnumEquipmentType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class EquipmentRepository : BaseRepository<Equipment, EquipmentTable>(
    table = EquipmentTable,
    entityClass = Equipment::class
) {
    override val entityName = "Equipment"

    /** Весь инвентарь экипировки персонажа (и надетое, и в сумке) */
    fun findByCharacter(characterId: Long): List<Equipment> = transaction {
        table.selectAll()
            .where { table.characterId eq characterId }
            .orderBy(table.id, SortOrder.ASC)
            .map(::toEntity)
    }

    /** Только то, что надето */
    fun findEquipped(characterId: Long): List<Equipment> = transaction {
        table.selectAll()
            .where { (table.characterId eq characterId) and (table.equippedSlot.isNotNull()) }
            .orderBy(table.slot, SortOrder.ASC)
            .map(::toEntity)
    }

    /** Только то, что в сумке (не надето) */
    fun findInBag(characterId: Long): List<Equipment> = transaction {
        table.selectAll()
            .where { (table.characterId eq characterId) and (table.equippedSlot.isNull()) }
            .orderBy(table.id, SortOrder.ASC)
            .map(::toEntity)
    }

    /** Что надето в конкретном слоте */
    fun findBySlot(characterId: Long, slot: EnumEquipmentType): Equipment? = transaction {
        table.selectAll()
            .where { (table.characterId eq characterId) and (table.equippedSlot eq slot) }
            .singleOrNull()
            ?.let(::toEntity)
    }
}
