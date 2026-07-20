package base.entity

import org.bson.types.ObjectId

interface StockEntity {
    var _id: ObjectId

    fun getId(): String = _id.toHexString()
    fun setId(id: String) {
        _id = ObjectId(id)
    }
}