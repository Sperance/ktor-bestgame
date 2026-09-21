package features.data.items

import base.entity.StockEntity
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class Items(

    /**
     * Стабильный код предмета. Им предмет ссылается на свой текст
     * в файлах локализации: item.<code>.name и .description.
     */
    val code: String,

    val category: String,
    val subCategory: String,
    val image: String? = null,
    val price: Long = 0,

    override var _id: String = ObjectId().toHexString(),
) : StockEntity