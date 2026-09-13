package ru.descend.infrastructure.cache

import ru.descend.features.equipment.model.Equipment
import ru.descend.features.equipment.persistence.EquipmentRepository

class EquipmentCache(repository: EquipmentRepository) : MongoCache<Equipment, EquipmentRepository>(repository)
