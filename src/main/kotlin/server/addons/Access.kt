package server.addons

import base.exception.model.AuthExceptions
import features.data.auth.AuthSessionRepository
import features.data.character.CharacterRepository
import features.data.user.UserRepository
import features.logic.auth.AccessPolicy
import features.logic.auth.AccessPolicy.Need
import features.logic.auth.Caller
import features.logic.auth.Tokens
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

/** Вызывающий, положенный в атрибуты запроса - для кода, которому удобнее взять его отсюда. */
val CallerKey = AttributeKey<Caller>("caller")

/**
 * Проверка доступа перед каждым маршрутом.
 *
 * Уровень доступа берётся из [AccessPolicy]; здесь к нему добавляется то, что без базы не
 * проверить: чья это сессия, и принадлежит ли вызывающему персонаж, от имени которого он
 * действует. Почти каждый игровой маршрут принимает `characterId`, поэтому одна проверка
 * здесь закрывает их все: подставить чужой персонаж больше нельзя нигде. Предметы, лоты и
 * гнёзда уже проверяются репозиториями на принадлежность этому персонажу.
 *
 * Администратор проходит проверку принадлежности - ему нужно работать с чужими персонажами.
 */
fun Application.configureAccess() {
    val sessions by inject<AuthSessionRepository>()
    val users by inject<UserRepository>()
    val characters by inject<CharacterRepository>()

    intercept(ApplicationCallPipeline.Plugins) {
        if (call.request.httpMethod == HttpMethod.Options) return@intercept
        val path = call.request.path()
        val query = call.request.queryParameters
        val need = AccessPolicy.need(call.request.httpMethod.value, path) { query[it] }
        if (need == Need.PUBLIC) return@intercept

        val token = Tokens.fromHeader(call.request.headers[HttpHeaders.Authorization])
            ?: throw AuthExceptions.funExceptionNoToken("access", path)
        val session = sessions.resolve(token) ?: throw AuthExceptions.funExceptionBadToken("access", path)
        val user = users.findById(session.userId)?.takeIf { it.isActive }
            ?: throw AuthExceptions.funExceptionBadToken("access", path)
        val caller = Caller(user, token)

        if (need == Need.ADMIN && !caller.isAdmin) throw AuthExceptions.funExceptionAdminOnly("access", path)

        if (!caller.isAdmin) {
            query["userId"]?.let { if (it != user._id) throw AuthExceptions.funExceptionNotYourAccount("access", it) }
            // Персонаж по characterId, а в общем CRUD персонажей - по id.
            val characterId = query["characterId"] ?: query["id"]?.takeIf { path.trimEnd('/') == "/api/v1/character" }
            characterId?.let { id ->
                val owner = characters.ownerOf(id)
                // Несуществующий персонаж пропускается: маршрут сам скажет, что его нет, а
                // ответ «не ваш» на «нет такого» подтверждал бы, что чужой id существует.
                if (owner != null && owner != user._id) throw AuthExceptions.funExceptionNotYourCharacter("access", id)
            }
        }

        call.attributes.put(CallerKey, caller)
        withContext(caller) { proceed() }
    }
}
