package ru.descend.exileforge

import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import ru.descend.exileforge.application.koin.cacheModule
import ru.descend.exileforge.application.koin.contentModule
import ru.descend.exileforge.application.koin.repositoryModule
import ru.descend.exileforge.application.koin.routeModule
import ru.descend.exileforge.application.koin.serviceModule
import ru.descend.exileforge.base.route.RouteRegistrar
import ru.descend.exileforge.base.route.RouteRegistry
import ru.descend.exileforge.config.DatabaseSeeder
import ru.descend.exileforge.features.caches.BlockListCache
import ru.descend.exileforge.features.data.routeTiming.RouteTimings
import ru.descend.exileforge.features.logic.hero.HeroSnapshots
import ru.descend.exileforge.server.addons.RateKeys
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Граф Koin собирается целиком без базы: каждый конструктор находит свои зависимости, циклов нет.
 * Модули с `createdAtStart` (резервные копии, монитор) не поднимаются - они запускают фоновые задачи.
 */
class KoinGraphTest {
    @Test
    fun every_route_service_and_repository_is_constructible() {
        val koin = startKoin { modules(contentModule, repositoryModule, serviceModule, cacheModule, routeModule) }.koin
        try {
            assertEquals(8, koin.getAll<RouteRegistrar>().size)
            koin.get<RouteRegistry>()
            koin.get<DatabaseSeeder>()
            koin.get<HeroSnapshots>()
            koin.get<RateKeys>()
            koin.get<RouteTimings>()
            koin.get<BlockListCache>()
        } finally {
            stopKoin()
        }
    }
}
