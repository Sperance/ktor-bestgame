package ru.descend.features.items.http

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import ru.descend.features.icons.IconBindings
import ru.descend.features.items.model.Items
import ru.descend.features.items.persistence.ItemsRepository
import ru.descend.infrastructure.cache.ItemsCache
import ru.descend.shared.http.ApiMongoResponse
import ru.descend.shared.http.BaseRoute

class ItemsRoute(repo: ItemsRepository) : BaseRoute<Items, Items>(
    repository = repo,
    entitySerializer = Items.serializer(),
    responseSerializer = Items.serializer(),
    // Старые документы без иконки получают её при чтении; сохранённые данные не переписываются.
    toResponse = { if (it.icon != null) it else it.copy(icon = IconBindings.resolvedIcon(it)) }
), KoinComponent {
    private val itemsCache: ItemsCache by inject()

    override fun additionalRoutes(route: Route) = with(route) {
        get("/cache/hash") {
            val data = itemsCache.getCacheHash()
            call.respond(ApiMongoResponse.ok(data))
        }
    }
}
