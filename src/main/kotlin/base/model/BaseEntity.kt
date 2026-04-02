package base.model

/**
 * Контракт Response-DTO. Поле [version] обязательно для optimistic locking.
 */
interface BaseEntity {
    val id: Long?
    val version: Long
}

/** Контракт Create-запроса */
interface CreateRequest

/** Контракт Update-запроса. Клиент обязан прислать version, полученный при чтении. */
interface UpdateRequest {
    val version: Long
}