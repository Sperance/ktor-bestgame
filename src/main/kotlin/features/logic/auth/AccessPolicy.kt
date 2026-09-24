package features.logic.auth

/**
 * Кто может звать какой маршрут - одной таблицей.
 *
 * До 0.21.0 сервер не знал, кто делает запрос: любой, кто знал адрес, мог продать чужой
 * предмет или записать себе роль ADMIN через общий CRUD. Теперь каждый запрос проходит здесь,
 * до маршрута, и правила собраны в одном месте, а не разбросаны по маршрутам: так их можно
 * прочитать целиком и проверить тестом без базы.
 *
 * Здесь только уровень доступа. Кому принадлежит персонаж, решает [server.addons.configureAccess]
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

    private val publicSystem = setOf("/system/routes", "/system/health", "/system/version", "/system/stats")

    /**
     * Коллекции, в которых лежат чужие данные: аккаунты, персонажи, их вещи, лоты, промокоды.
     * Целиком их читает только администратор; игрок видит своё через игровые маршруты.
     */
    val privateCollections = setOf(
        "user", "character", "characterequipment", "auctionlot", "redemptioncodes", "blocklist", "authsession",
    )

    /** Игровые маршруты, которыми администратор выдаёт что-то из ничего. */
    private val adminRoutes = setOf(
        "/api/v1/character/inventory/itemToInventory",
        "/api/v1/character/inventory/experience",
        "/api/v1/character/inventory/addItem",
        "/system/shutdown",
        "/system/exceptions",
    )

    /**
     * @param query параметр строки запроса по имени - нужен, чтобы отличить чтение одного
     * своего персонажа от чтения всех сразу
     */
    fun need(method: String, rawPath: String, query: (String) -> String? = { null }): Need {
        val path = rawPath.trimEnd('/').ifEmpty { "/" }
        val verb = method.uppercase()

        if (path.startsWith("/locale/") || path.startsWith("/icons/") || path.startsWith("/portraits/")) return Need.PUBLIC
        if (path == "/static/index.json") return Need.PUBLIC
        // Справочники мира - те же коллекции, что читаются через /api вошедшим игроком.
        if (path.startsWith("/world/")) return Need.SIGNED_IN
        if (path in publicSystem) return Need.PUBLIC
        if (verb == "POST" && path in publicPosts) return Need.PUBLIC
        if (path in adminRoutes) return Need.ADMIN
        if (path.startsWith("/system/")) return Need.ADMIN
        if (!path.startsWith("/api/")) return Need.PUBLIC

        if (path.startsWith("/api/v1/user/search")) return Need.ADMIN

        val segments = path.removePrefix("/api/v1/").split('/')
        val collection = segments.first()
        val generic = segments.size == 1 || (segments.size == 2 && segments[1] in setOf("paged", "count"))
        if (!generic) return Need.SIGNED_IN

        return when {
            // Запись в любую коллекцию - дело администратора. Игрок меняет мир только игровыми
            // маршрутами, где сервер сам проверяет правила. Исключение - собственный персонаж:
            // создать и отпустить его игрок может сам, а чей он, проверяется отдельно.
            verb != "GET" && verb != "HEAD" ->
                if (collection == "character" && segments.size == 1 && verb in setOf("POST", "DELETE")) Need.SIGNED_IN
                else Need.ADMIN
            // Своего персонажа по id читать можно: принадлежность проверяется отдельно.
            // Без id это список всех персонажей сервера - он только для администратора.
            collection == "character" && segments.size == 1 && !query("id").isNullOrBlank() -> Need.SIGNED_IN
            collection in privateCollections -> Need.ADMIN
            else -> Need.SIGNED_IN
        }
    }
}
