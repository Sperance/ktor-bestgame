package features.data.property

import application.enums.EnumStatType
import base.entity.StockEntity
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Property(
    var code: String = "",
    var name: String = "",
    var description: String = "",
    var type: EnumStatType = EnumStatType.STOCK,
    var image: String? = null,
    var step: Byte,

    @Contextual
    override var _id: ObjectId = ObjectId(),
) : StockEntity