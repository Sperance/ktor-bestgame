package ru.descend.features.redemptioncodes.model

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import ru.descend.shared.model.VersionedEntity
import ru.descend.shared.extensions.now

@Serializable
@kotlinx.serialization.SerialName("features.data.redemptionCodes.RedemptionCodes")
data class RedemptionCodes(
    val code: String,
    val treasure: List<RedemptionItem>,
    val description: String? = null,
    var used: Long = 0,
    var expiredAt: LocalDateTime? = null,

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity

@Serializable
@kotlinx.serialization.SerialName("features.data.redemptionCodes.RedemptionItem")
data class RedemptionItem(
    val itemId: String,
    val amount: Double
)
