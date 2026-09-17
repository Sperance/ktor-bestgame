package ru.descend.bootstrap.di

import org.koin.dsl.module
import ru.descend.infrastructure.backup.MongoBackupManager

val backupModule = module {
    single(createdAtStart = true) {
        MongoBackupManager(
            maxDays = 7,
            maxBackupsCount = 5,
            compress = true
        ).apply {
            start()
        }
    }
}

