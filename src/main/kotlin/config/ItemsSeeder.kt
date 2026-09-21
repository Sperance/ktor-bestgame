package config

import base.exception.model.ItemsExceptions
import extensions.toStableObjectId
import features.data.items.Items
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Начальные данные обычных предметов в коллекции `Items`.
 *
 * Сами строки лежат в `resources/content/items.json` - см. [ContentResource].
 * Валютные сферы живут отдельно, в [CurrencySeeder]: у них своя категория
 * и своё поведение. Здесь всё остальное - сырьё и расходники.
 *
 * Названий и описаний тут нет: документ хранит только код, а текст лежит
 * в файлах локализации под ключами `item.<код>.name` и `.description`.
 */
object ItemsSeeder {

    const val FILE = "items.json"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ItemRecord(
        val code: String,
        val category: String,
        val subCategory: String,
        val price: Long,
    )

    @Serializable
    private data class ItemsDocument(val items: List<ItemRecord> = emptyList())

    /**
     * Документы обычных предметов.
     *
     * _id выводится из кода и стабилен, поэтому пересев не ломает ссылки
     * из инвентарей персонажей - а дубликат кода склеил бы двум предметам один _id.
     */
    fun seed(): List<Items> {
        val records = json.decodeFromString(ItemsDocument.serializer(), ContentResource.read(FILE)).items

        val duplicates = records.groupBy { it.code }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty())
            throw ItemsExceptions.funException("seed", "Duplicate item codes: $duplicates")

        return records.map { record ->
            Items(
                code = record.code,
                category = record.category,
                subCategory = record.subCategory,
                price = record.price,
                _id = record.code.toStableObjectId()
            )
        }
    }
}
