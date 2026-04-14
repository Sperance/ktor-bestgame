package application.model

import kotlinx.serialization.Serializable

@Serializable
data class ItemStock(
    val item_id: Long,
    var quantity: Int
) {
    override fun toString(): String {
        return "ItemStock(item_id=$item_id, quantity=$quantity)"
    }
}