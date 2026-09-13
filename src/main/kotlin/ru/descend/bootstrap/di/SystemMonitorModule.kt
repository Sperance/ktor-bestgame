package ru.descend.bootstrap.di

import org.koin.dsl.module
import ru.descend.infrastructure.monitoring.SystemMonitor

val systemMonitorModule = module {
    single(createdAtStart = true) {
        SystemMonitor.apply {
            start(intervalHours = 1)
        }
    }
}

