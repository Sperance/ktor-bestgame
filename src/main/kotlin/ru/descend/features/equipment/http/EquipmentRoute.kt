package ru.descend.features.equipment.http

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.descend.features.equipment.model.Equipment
import ru.descend.features.icons.IconBindings
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.infrastructure.cache.EquipmentCache
import ru.descend.shared.http.ApiMongoResponse
import ru.descend.shared.http.BaseRoute

class EquipmentRoute(repo: EquipmentRepository) : BaseRoute<Equipment, Equipment>(
    repository = repo,
    entitySerializer = Equipment.serializer(),
    responseSerializer = Equipment.serializer(),
    // Старые документы без иконки получают её при чтении; сохранённые данные не переписываются.
    toResponse = { it.also { equipment -> equipment.icon = IconBindings.resolvedIcon(equipment) } }
), KoinComponent {
    private val equipmentCache: EquipmentCache by inject()

    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache/hash") {
            val data = equipmentCache.getCacheHash()
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
