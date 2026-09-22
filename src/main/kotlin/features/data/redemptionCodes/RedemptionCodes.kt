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
 * Что промокод выдаёт.
 *
 * Награды разного рода лежат одним списком, потому что администратор составляет
 * их одним списком: «опыт, немного золота и три сферы хаоса» это одна мысль, а не
 * три поля. [kind] говорит, как читать запись, - у предмета и экипировки заполнен
 * [itemId], у опыта и золота только [amount].
 */
@Serializable
data class RedemptionItem(
    val kind: RedemptionKind = RedemptionKind.ITEM,
    val itemId: String = "",
    val amount: Double
)

/**
 * Род награды.
 *
 * [ITEM] - стакающийся предмет из `items`, включая валютные сферы: [itemId] это его
 * документ, [amount] - количество.
 * [EQUIPMENT] - шаблон из `equipment`: сервер роллит экземпляр столько раз, сколько
 * велит [amount], потому что две копии одного шаблона это два разных предмета.
 * [EXPERIENCE] и [GOLD] предмета не имеют - у них есть только величина.
 */
@Serializable
enum class RedemptionKind { ITEM, EQUIPMENT, EXPERIENCE, GOLD }
