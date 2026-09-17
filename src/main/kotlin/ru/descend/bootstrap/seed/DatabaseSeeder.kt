package ru.descend.bootstrap.seed

import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import ru.descend.features.poe.seed.PoeSeeder
import ru.descend.shared.extensions.printLog

/** Production catalog is always additive. Demo identities require an explicit opt-in. */
object DatabaseSeeder : KoinComponent {
    suspend fun seed() {
        printLog("Database seeding started")
        PoeSeeder.seed()
        get<ru.descend.features.passives.persistence.PassiveTreeRepository>().seed()
        if (System.getenv("SEED_DEMO_DATA") == "true") {
            DemoSeeder(get(), get()).seed(requireNotNull(System.getenv("SEED_ADMIN_PASSWORD")) {
                "SEED_ADMIN_PASSWORD is required when SEED_DEMO_DATA=true"
            })
        }
        printLog("Database seeding completed")
    }
}
