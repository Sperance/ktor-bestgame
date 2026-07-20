package features.data.enums.equipment

import base.route.BaseRoute

class EquipmentRoute(
    repo: EquipmentRepository
) : BaseRoute<Equipment, Equipment>(
    repository = repo,
    entitySerializer = Equipment.serializer(),
    responseSerializer = Equipment.serializer(),
    toResponse = { it }
)