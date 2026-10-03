package ru.descend.exileforge.server.addons
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.util.AttributeKey
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject
import ru.descend.exileforge.base.exception.model.AuthExceptions
import ru.descend.exileforge.features.data.auth.AuthSessionRepository
import ru.descend.exileforge.features.data.hero.HeroRepository
import ru.descend.exileforge.features.data.user.UserRepository
import ru.descend.exileforge.features.logic.auth.AccessPolicy
import ru.descend.exileforge.features.logic.auth.AccessPolicy.Need
import ru.descend.exileforge.features.logic.auth.Caller
import ru.descend.exileforge.features.logic.auth.SessionCache
import ru.descend.exileforge.features.logic.auth.Tokens
import ru.descend.exileforge.features.logic.hero.HeroContext
import ru.descend.exileforge.features.logic.hero.HeroContextKey

/** Вызывающий, положенный в атрибуты запроса - для кода, которому удобнее взять его отсюда. */
val CallerKey = AttributeKey<Caller>("caller")

/**
 * Проверка доступа перед каждым маршрутом.
 *
 * Уровень доступа берётся из [AccessPolicy]; здесь к нему добавляется то, что без базы не
 * проверить: чья это сессия, и принадлежит ли вызывающему персонаж, от имени которого он
 * действует. Почти каждый игровой маршрут принимает `heroId`, поэтому одна проверка
 * здесь закрывает их все: подставить чужого героя больше нельзя нигде. Вещи лежат в документе
 * героя, лоты проверяются репозиторием аукциона на принадлежность.
 *
 * Администратор проходит проверку принадлежности - ему нужно работать с чужими персонажами.
 */
fun Application.configureAccess() {
    val sessions by inject<AuthSessionRepository>()
    val users by inject<UserRepository>()
    val heroes by inject<HeroRepository>()
    val blocks by inject<ru.descend.exileforge.features.caches.BlockListCache>()

    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.httpMethod == HttpMethod.Options) return@intercept
        val path = call.request.path()
        val query = call.request.queryParameters
        val need = AccessPolicy.need(call.request.httpMethod.value, path) { query[it] }
        if (need == Need.PUBLIC) return@intercept

        val token = Tokens.fromHeader(call.request.headers[HttpHeaders.Authorization])
            ?: throw AuthExceptions.funExceptionNoToken("access", path)
        // Сессия и аккаунт - из кеша на минуту (1.53.0): две коллекции на каждую команду героя читать незачем
        val tokenHash = Tokens.hash(token)
        val (session, user) = SessionCache.get(tokenHash) ?: run {
            val session = sessions.resolve(token) ?: throw AuthExceptions.funExceptionBadToken("access", path)
            val user = users.findById(session.userId)?.takeIf { it.isActive } ?: throw AuthExceptions.funExceptionBadToken("access", path)
            SessionCache.put(tokenHash, session, user)
            session to user
        }
        if (blocks.isUserBlocked(user._id)) {
            sessions.revokeAll(user._id)
            throw AuthExceptions.funExceptionBlocked("access")
        }
        val caller = Caller(user, token)

        if (need == Need.ADMIN && !caller.isAdmin) throw AuthExceptions.funExceptionAdminOnly("access", path)
        if (need == Need.TESTER && !caller.isTester) throw AuthExceptions.funExceptionAdminOnly("access", path)

        if (!caller.isAdmin) query["userId"]?.let { if (it != user._id) throw AuthExceptions.funExceptionNotYourAccount("access", it) }
        // Герой по heroId, а в общем CRUD героев - по id.
        val heroId = query["heroId"] ?: query["id"]?.takeIf { AccessPolicy.canonical(path) == "/api/v1/hero" }
        heroId?.takeIf { it.isNotBlank() }?.let { id ->
            // Несуществующий персонаж пропускается: маршрут сам скажет, что его нет, а
            // ответ «не ваш» на «нет такого» подтверждал бы, что чужой id существует.
            val owner = heroes.ownerOf(id) ?: return@let
            if (owner != user._id && !caller.isAdmin) throw AuthExceptions.funExceptionNotYourCharacter("access", id)
            call.attributes.put(HeroContextKey, HeroContext(id, owner))
        }

        call.attributes.put(CallerKey, caller)
        withContext(caller) { proceed() }
    }
}
