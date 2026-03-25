package application.data.inventory

import application.data.BaseDTO
import application.model.ItemStock

class SnapshotInventory(
    val _id: Long,
    var _items: MutableSet<ItemStock>?,
) : BaseDTO() {
    override fun toString(): String {
        return "SnapshotInventory(_id=$_id, _items=$_items)"
    }
}
