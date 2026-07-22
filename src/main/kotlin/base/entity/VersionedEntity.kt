package base.entity

import kotlinx.datetime.LocalDateTime

interface VersionedEntity : StockEntity {
    var version: Long
    var deleted: Boolean
    val createdAt: LocalDateTime
    var updatedAt: LocalDateTime
}
