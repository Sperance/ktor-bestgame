package ru.descend.features.redemptioncodes.http

import io.ktor.server.routing.*
import ru.descend.features.redemptioncodes.model.RedemptionCodes
import ru.descend.features.redemptioncodes.persistence.RedemptionCodesRepository
import ru.descend.shared.http.BaseRoute

class RedemptionCodesRoute(val repo: RedemptionCodesRepository) : BaseRoute<RedemptionCodes, RedemptionCodes>(repo, RedemptionCodes.serializer(), RedemptionCodes.serializer(), { it })
