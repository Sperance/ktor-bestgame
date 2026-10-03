package ru.descend.exileforge.config

import ru.descend.exileforge.extensions.Millis

/**
 * Настройки сервера, не баланс игры: сроки, лимиты, античит и окно тестирования. Читаются из
 * окружения с прежними значениями по умолчанию; баланс живёт в JSON контента (`content/`).
 */
data class ServerSettings(
    /** Сколько дней живёт сессия без использования. */
    val sessionDays: Int,
    /** Живых сессий на аккаунт: новая вытесняет самую старую. */
    val sessionsPerUser: Int,
    /** Сколько дней лежит письмо. */
    val mailDays: Int,
    /** Больше событий за одну отправку журнала захода не принимается. */
    val runMaxEvents: Int,
    /** Больше убийств в секунду с начала захода не бывает даже у сильнейших героев. */
    val killsPerSecond: Double,
    /** Отметка захода участника гильдии пишется не чаще раза в столько. */
    val guildSeenStepMs: Long,
    /** Отметка чтения героя для ремёсел пишется не чаще раза в столько. */
    val craftsSeenStepMs: Long,
    val testerMaxGold: Long,
    val testerMaxPoints: Int,
    val testerMaxBatch: Int,
    /** Потолок минимального уровня для входа в гильдию. */
    val guildMaxMinLevel: Int,
) {
    companion object {
        fun fromEnv(): ServerSettings = fromEnv(::systemEnv)

        private fun systemEnv(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

        fun fromEnv(env: (String) -> String?) = ServerSettings(
            sessionDays = env("SESSION_DAYS")?.toIntOrNull() ?: 30,
            sessionsPerUser = env("SESSIONS_PER_USER")?.toIntOrNull() ?: 5,
            mailDays = env("MAIL_DAYS")?.toIntOrNull() ?: 30,
            runMaxEvents = env("RUN_MAX_EVENTS")?.toIntOrNull() ?: 64,
            killsPerSecond = env("KILLS_PER_SECOND")?.toDoubleOrNull() ?: 4.0,
            guildSeenStepMs = env("GUILD_SEEN_STEP_MINUTES")?.toLongOrNull()?.times(Millis.MINUTE) ?: (10 * Millis.MINUTE),
            craftsSeenStepMs = env("CRAFTS_SEEN_STEP_SECONDS")?.toLongOrNull()?.times(1000) ?: Millis.MINUTE,
            testerMaxGold = env("TESTER_MAX_GOLD")?.toLongOrNull() ?: 1_000_000_000L,
            testerMaxPoints = env("TESTER_MAX_POINTS")?.toIntOrNull() ?: 1_000,
            testerMaxBatch = env("TESTER_MAX_BATCH")?.toIntOrNull() ?: 50,
            guildMaxMinLevel = env("GUILD_MAX_MIN_LEVEL")?.toIntOrNull() ?: 1000,
        )
    }
}

/** Часы сервера: один источник «сейчас» для сервисов, подменяемый в тестах. */
fun interface ServerClock {
    fun now(): Long
}
