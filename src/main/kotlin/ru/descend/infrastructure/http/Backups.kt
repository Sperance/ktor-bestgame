package ru.descend.infrastructure.http

import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject
import ru.descend.infrastructure.backup.MongoBackupManager
import ru.descend.shared.extensions.saveChildren
import ru.descend.shared.http.ApiMongoResponse

fun Application.configureBackups() {
    val backupManager: MongoBackupManager by inject()

    routing {
        post("/admin/backup") {
            backupManager.createBackupNow()
            call.respond(ApiMongoResponse.ok("Success"))
        }

        get("/admin/backups") {
            val backups = backupManager.getAllBackups()
            call.respond(backups)
        }
    }.saveChildren()
}
