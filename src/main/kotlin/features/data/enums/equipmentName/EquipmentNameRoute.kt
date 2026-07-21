package features.data.enums.equipmentName

import base.route.BaseRoute
import features.data.enums.items.Items
import features.data.enums.items.ItemsRepository

class EquipmentNameRoute(
    repo: EquipmentNameRepository
) : BaseRoute<EquipmentName, EquipmentName>(
    repository = repo,
    entitySerializer = EquipmentName.serializer(),
    responseSerializer = EquipmentName.serializer(),
    toResponse = { it }
)