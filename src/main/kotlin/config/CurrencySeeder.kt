package config

import application.enums.EnumCurrencyOrb
import base.exception.model.ItemsExceptions
import extensions.toStableObjectId
import features.data.items.Items
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Начальные данные валютных сфер в коллекции `Items`.
 *
 * Список и цены лежат в `resources/content/currency.json` - см. [ContentResource].
 * Сфера - обычный предмет инвентаря: лежит в плоском массиве персонажа
 * строкой "itemId:amount". От прочих предметов её отличает категория
 * [EnumCurrencyOrb.CATEGORY], а подкатегория связывает документ
 * с его поведением в CurrencyApplier.
 */
object CurrencySeeder {

    const val FILE = "currency.json"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class OrbRecord(val orb: EnumCurrencyOrb, val price: Long)

    @Serializable
    private data class CurrencyDocument(val currency: List<OrbRecord> = emptyList())

    /**
     * Документы сфер для коллекции `Items`.
     *
     * _id выводится из кода сферы и стабилен, поэтому пересев валюты
     * не ломает ссылки из инвентарей персонажей.
     */
    fun seed(): List<Items> {
        val records = json.decodeFromString(CurrencyDocument.serializer(), ContentResource.read(FILE)).currency

        val duplicates = records.groupBy { it.orb }.filterValues { it.size > 1 }.keys
        if (duplicates.isNotEmpty())
            throw ItemsExceptions.funException("seed", "Duplicate currency orbs: $duplicates")

        return records.map { record ->
            Items(
                code = record.orb.name,
                category = EnumCurrencyOrb.CATEGORY,
                subCategory = record.orb.name,
                price = record.price,
                _id = record.orb.name.toStableObjectId()
            )
        }
    }
}
