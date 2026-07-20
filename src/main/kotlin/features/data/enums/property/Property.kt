package features.data.enums.property

import application.enums.EnumStatType
import base.entity.StockEntity
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Property(
    val code: String = "",
    val name: String = "",
    val description: String = "",
    val type: EnumStatType = EnumStatType.STOCK,
    val image: String? = null,

    @Contextual
    override var _id: ObjectId = ObjectId(),
) : StockEntity