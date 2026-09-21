package config

import extensions.toStableObjectId
import features.data.items.Items

/**
 * Начальные данные обычных предметов в коллекции `Items`.
 *
 * Валютные сферы живут отдельно, в [CurrencySeeder]: у них своя категория
 * и своё поведение. Здесь всё остальное - сырьё и расходники.
 *
 * Названий и описаний тут нет: документ хранит только код, а текст лежит
 * в файлах локализации под ключами `item.<код>.name` и `.description`.
 */
object ItemsSeeder {

    private data class ItemTemplate(
        val code: String,
        val category: String,
        val subCategory: String,
        val price: Long,
    )

    private val templates = listOf(
        ItemTemplate("WOOD_LOG", category = "WOOD_STOCK", subCategory = "LOG", price = 10),
        ItemTemplate("STONE_ROUGH", category = "STONE_STOCK", subCategory = "STONE", price = 12),
        ItemTemplate("STONE_POLISHED", category = "STONE_STOCK", subCategory = "STONE", price = 22),
        ItemTemplate("POTION_HEALTH", category = "CONSUMABLE", subCategory = "HEALTH", price = 80),
    )

    /**
     * Документы обычных предметов.
     *
     * _id выводится из кода и стабилен, поэтому пересев не ломает ссылки
     * из инвентарей персонажей.
     */
    fun seed(): List<Items> = templates.map { template ->
        Items(
            code = template.code,
            category = template.category,
            subCategory = template.subCategory,
            price = template.price,
            _id = template.code.toStableObjectId()
        )
    }
}
