package ru.descend.exileforge.features.logic.auth
import ru.descend.exileforge.application.enums.EnumUserRoles
import ru.descend.exileforge.features.data.user.User
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

    /** Окно тестирования (1.69.0): тестировщику и администратору. */
    val isTester: Boolean get() = isAdmin || user.role == EnumUserRoles.TESTER
    companion object Key : CoroutineContext.Key<Caller>
}

suspend fun caller(): Caller? = coroutineContext[Caller]
