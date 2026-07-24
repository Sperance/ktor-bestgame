package server.addons

import base.route.ApiMongoResponse
import config.MongoBackupManager
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject
import kotlin.getValue

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
    }
}