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

/**
 * Что промокод выдаёт: у предмета и вещи заполнен [item] - код стопки или шаблона, - у опыта и золота
 * только [amount]. Награды разного рода лежат одним списком, как их составляет администратор.
 */
@Serializable
data class RedemptionItem(
    val kind: RedemptionKind = RedemptionKind.ITEM,
    val item: String = "",
    val amount: Double,
)

/** Род награды: стопка из `items`, вещь из `equipment` (столько копий, сколько велит количество), опыт, золото. */
@Serializable
enum class RedemptionKind { ITEM, EQUIPMENT, EXPERIENCE, GOLD }
