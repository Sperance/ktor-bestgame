package features.caches

import features.data.equipment.Equipment
import features.data.equipment.EquipmentRepository

object EquipmentCache : MongoCache<Equipment, EquipmentRepository>()