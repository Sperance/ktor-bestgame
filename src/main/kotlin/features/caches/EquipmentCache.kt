package features.caches

import features.data.equipment.Equipment
import features.data.equipment.EquipmentRepository

class EquipmentCache(repository: EquipmentRepository) : MongoCache<Equipment, EquipmentRepository>(repository)