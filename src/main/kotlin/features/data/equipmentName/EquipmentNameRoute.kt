package features.data.equipmentName

import base.route.BaseRoute

class EquipmentNameRoute(repo: EquipmentNameRepository) : BaseRoute<EquipmentName, EquipmentName>(
    repository = repo,
    entitySerializer = EquipmentName.serializer(),
    responseSerializer = EquipmentName.serializer(),
    toResponse = { it }
)