package config

import application.enums.EnumCurrencyOrb
import extensions.toStableObjectId
import features.data.items.Items

/**
 * Начальные данные валютных сфер в коллекции `Items`.
 *
 * Сфера - обычный предмет инвентаря: лежит в плоском массиве персонажа
 * строкой "itemId:amount". От прочих предметов её отличает категория
 * [EnumCurrencyOrb.CATEGORY], а подкатегория связывает документ
 * с его поведением в CurrencyApplier.
 */
object CurrencySeeder {

    private data class OrbTemplate(
        val orb: EnumCurrencyOrb,
        val price: Long,
    )

    private val templates = listOf(
        OrbTemplate(EnumCurrencyOrb.ORB_OF_TRANSMUTATION, price = 10),
        OrbTemplate(EnumCurrencyOrb.ORB_OF_AUGMENTATION, price = 20),
        OrbTemplate(EnumCurrencyOrb.ORB_OF_ALTERATION, price = 40),
        OrbTemplate(EnumCurrencyOrb.ORB_OF_ALCHEMY, price = 120),
        OrbTemplate(EnumCurrencyOrb.REGAL_ORB, price = 350),
        OrbTemplate(EnumCurrencyOrb.CHAOS_ORB, price = 300),
        OrbTemplate(EnumCurrencyOrb.EXALTED_ORB, price = 25000),
        OrbTemplate(EnumCurrencyOrb.DIVINE_ORB, price = 6000),
        OrbTemplate(EnumCurrencyOrb.ORB_OF_ANNULMENT, price = 4000),
        OrbTemplate(EnumCurrencyOrb.ORB_OF_SCOURING, price = 200),
        OrbTemplate(EnumCurrencyOrb.BLESSED_ORB, price = 500),
        OrbTemplate(EnumCurrencyOrb.VAAL_ORB, price = 800),
        OrbTemplate(EnumCurrencyOrb.ORB_OF_CHANCE, price = 60),
        OrbTemplate(EnumCurrencyOrb.MIRROR_OF_KALANDRA, price = 10_000_000),
    )

    /**
     * Документы сфер для коллекции `Items`.
     *
     * _id выводится из кода сферы и стабилен, поэтому пересев валюты
     * не ломает ссылки из инвентарей персонажей.
     */
    fun seed(): List<Items> = templates.map { template ->
        Items(
            code = template.orb.name,
            category = EnumCurrencyOrb.CATEGORY,
            subCategory = template.orb.name,
            price = template.price,
            _id = template.orb.name.toStableObjectId()
        )
    }
}
