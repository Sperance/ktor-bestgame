package features.caches

import features.data.equipment.equipment_data.Equipment
import features.data.equipment.EquipmentRepository

class EquipmentCache(repository: EquipmentRepository) : MongoCache<Equipment, EquipmentRepository>(repository)