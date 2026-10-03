package ru.descend.exileforge

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import ru.descend.exileforge.application.koin.cacheModule
import ru.descend.exileforge.application.koin.contentModule
import ru.descend.exileforge.application.koin.repositoryModule
import ru.descend.exileforge.application.koin.routeModule
import ru.descend.exileforge.base.route.RouteRegistry
import ru.descend.exileforge.features.caches.BlockListCache
import ru.descend.exileforge.features.data.routeTiming.RouteTimings
import ru.descend.exileforge.features.logic.hero.HeroSnapshots
import ru.descend.exileforge.server.addons.RateKeys
import kotlin.test.Test

/**
 * Граф Koin собирается целиком без базы: каждый конструктор находит свои зависимости, циклов нет.
 * Модули с `createdAtStart` (резервные копии, монитор) не поднимаются - они запускают фоновые задачи.
 */
class KoinGraphTest {
    @Test
    fun every_route_service_and_repository_is_constructible() {
        val koin = startKoin { modules(contentModule, repositoryModule, cacheModule, routeModule) }.koin
        try {
            koin.get<RouteRegistry>()
            koin.get<HeroSnapshots>()
            koin.get<RateKeys>()
            koin.get<RouteTimings>()
            koin.get<BlockListCache>()
        } finally {
            stopKoin()
        }
    }
}
