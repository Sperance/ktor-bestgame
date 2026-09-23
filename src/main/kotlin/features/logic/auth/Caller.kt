package features.logic.auth

import application.enums.EnumUserRoles
import features.data.user.User
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Тот, кто делает текущий запрос.
 *
 * Лежит в контексте корутины всего запроса, поэтому его видит и маршрут, и репозиторий, и
 * валидация перед вставкой, - не нужно протаскивать пользователя параметром через все слои.
 * Вне запроса (сидинг, фоновые задачи) его нет, и [caller] возвращает null: это система.
 */
class Caller(val user: User, val token: String) : AbstractCoroutineContextElement(Key) {
    val isAdmin: Boolean get() = user.role == EnumUserRoles.ADMIN
    companion object Key : CoroutineContext.Key<Caller>
}

suspend fun caller(): Caller? = coroutineContext[Caller]
