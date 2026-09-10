package config

import features.data.equipment.equipment_data.Equipment
import features.poe.PoeCatalog

object EquipmentSeeder {
    fun seed(): List<Equipment> = PoeCatalog.bundled.releasedWearables().keys.sorted().map(PoeCatalog.bundled::equipment)
}
