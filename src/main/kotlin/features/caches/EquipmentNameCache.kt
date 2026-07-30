package features.caches

import features.data.equipmentName.EquipmentName
import features.data.equipmentName.EquipmentNameRepository

class EquipmentNameCache(repository: EquipmentNameRepository) : MongoCache<EquipmentName, EquipmentNameRepository>(repository)