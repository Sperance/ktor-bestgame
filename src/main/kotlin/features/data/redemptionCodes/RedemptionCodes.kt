package features.data.redemptionCodes

import base.entity.StockEntity
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class RedemptionCodes(
    val code: String,
    val treasure: List<RedemptionItem>,
    val description: String? = null,
    var used: Long = 0,
    var expiredAt: LocalDateTime? = null,

    override var _id: String = ObjectId().toHexString(),
) : StockEntity

@Serializable
data class RedemptionItem(
    val itemId: String,
    val amount: Double
)