package features.logic.auth

import io.ktor.http.decodeURLPart

/**
 * Кто может звать какой маршрут - одной таблицей.
 *
 * До 0.21.0 сервер не знал, кто делает запрос: любой, кто знал адрес, мог продать чужой
 * предмет или записать себе роль ADMIN через общий CRUD. Теперь каждый запрос проходит здесь,
 * до маршрута, и правила собраны в одном месте, а не разбросаны по маршрутам: так их можно
 * прочитать целиком и проверить тестом без базы.
 *
 * Здесь только уровень доступа. Кому принадлежит герой, решает [server.addons.configureAccess]
 * по параметрам запроса, потому что для этого нужна база.
 */
object AccessPolicy {

    enum class Need { PUBLIC, SIGNED_IN, ADMIN }

    /** Вход и регистрация - то, что делается без токена. */
    private val publicPosts = setOf(
        "/api/v1/user/login",
        "/api/v1/user/login/byDeviceId",
        "/api/v1/user/byDeviceId",
    )

    private val publicSystem = setOf("/system/routes", "/system/health")

    /**
     * Коллекции, в которых лежат чужие данные: аккаунты, персонажи, их вещи, лоты, промокоды.
     * Целиком их читает только администратор; игрок видит своё через игровые маршруты.
     */
    val privateCollections = setOf(
        "user", "hero", "auctionlot", "redemptioncodes", "blocklist", "authsession", "guild", "guildevent", "guildchat",
    )

    /** Игровые маршруты, которыми администратор выдаёт что-то из ничего. */
    private const val ADMIN_PREFIX = "/api/v1/hero/grant/"

    /**
     * Путь так, как его видит маршрутизатор Ktor: пустые сегменты отброшены, `%XX` раскрыты.
     * Политика обязана судить о том же пути, по которому выберут обработчик, иначе
     * `//api/v1/character` или `/api/v1/%63haracter` дошли бы до закрытого маршрута в обход проверки.
     */
    fun canonical(rawPath: String): String =
        rawPath.split('/').filter { it.isNotEmpty() }.joinToString("/", prefix = "/") { it.decodeURLPart() }

    /**
     * @param query параметр строки запроса по имени - нужен, чтобы отличить чтение одного
     * своего персонажа от чтения всех сразу
     */
    fun need(method: String, rawPath: String, query: (String) -> String? = { null }): Need {
        val path = canonical(rawPath)
        val verb = method.uppercase()

        // Контент (1.0.0) - те же файлы, что лежат в репозитории: секрета в них нет, а клиент качает их до входа.
        if (path.startsWith("/locale/") || path.startsWith("/icons/") || path.startsWith("/portraits/") || path.startsWith("/content/")) return Need.PUBLIC
        if (path == "/static/index.json") return Need.PUBLIC
        if (path in publicSystem) return Need.PUBLIC
        if (verb == "POST" && path in publicPosts) return Need.PUBLIC
        if (path.startsWith(ADMIN_PREFIX)) return Need.ADMIN
        if (path.startsWith("/system/")) return Need.ADMIN
        if (!path.startsWith("/api/")) return Need.PUBLIC

        val segments = path.removePrefix("/api/v1/").split('/')
        val collection = segments.first()
        val generic = segments.size == 1 || (segments.size == 2 && segments[1] in setOf("paged", "count"))
        if (!generic) return Need.SIGNED_IN

        return when {
            // Запись в любую коллекцию - дело администратора. Игрок меняет мир только игровыми
            // маршрутами, где сервер сам проверяет правила. Исключение - собственный герой:
            // создать и отпустить его игрок может сам, а чей он, проверяется отдельно.
            verb != "GET" && verb != "HEAD" ->
                if (collection == "hero" && segments.size == 1 && verb in setOf("POST", "DELETE")) Need.SIGNED_IN
                else Need.ADMIN
            // Своего героя по id читать можно: принадлежность проверяется отдельно.
            // Без id это список всех героев сервера - он только для администратора.
            collection == "hero" && segments.size == 1 && !query("id").isNullOrBlank() -> Need.SIGNED_IN
            collection in privateCollections -> Need.ADMIN
            else -> Need.SIGNED_IN
        }
    }
}
