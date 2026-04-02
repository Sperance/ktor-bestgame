package base.model

/**
 * Единственный контракт. Больше никаких CreateRequest / UpdateRequest.
 */
interface BaseEntity {
    val id: Long?
    val version: Long
}