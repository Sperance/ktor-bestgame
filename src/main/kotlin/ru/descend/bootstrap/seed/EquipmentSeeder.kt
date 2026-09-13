package ru.descend.bootstrap.seed

import ru.descend.features.equipment.model.Equipment
import ru.descend.features.poe.catalog.PoeCatalog

object EquipmentSeeder {
    fun seed(): List<Equipment> = PoeCatalog.bundled.releasedWearables().keys.sorted().map(PoeCatalog.bundled::equipment)
}
