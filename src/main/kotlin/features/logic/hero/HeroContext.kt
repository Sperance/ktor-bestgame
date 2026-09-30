package features.logic.hero

import io.ktor.server.application.ApplicationCall
import io.ktor.util.AttributeKey

/**
 * Доверенный герой запроса: проверка доступа ([server.addons.configureAccess]) кладёт его, лишь сверив
 * владельца по базе. Очередь героя ([HeroLocks]) и снимок после команды берут `heroId` отсюда, а не из
 * параметров: все слои запроса работают с одним и тем же, уже проверенным героем.
 */
data class HeroContext(val heroId: String, val ownerId: String)

val HeroContextKey = AttributeKey<HeroContext>("heroContext")

val ApplicationCall.heroContext: HeroContext? get() = attributes.getOrNull(HeroContextKey)
