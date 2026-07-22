package features.caches

import features.data.equipmentName.EquipmentName
import features.data.equipmentName.EquipmentNameRepository

object EquipmentNameCache : MongoCache<EquipmentName, EquipmentNameRepository>()