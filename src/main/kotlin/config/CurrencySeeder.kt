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
        val name: String,
        val description: String,
        val price: Long,
    )

    private val templates = listOf(
        OrbTemplate(
            EnumCurrencyOrb.ORB_OF_TRANSMUTATION,
            "Orb of Transmutation",
            "Обычный предмет становится магическим и получает аффиксы",
            price = 10
        ),
        OrbTemplate(
            EnumCurrencyOrb.ORB_OF_AUGMENTATION,
            "Orb of Augmentation",
            "Добавляет магическому предмету ещё один аффикс",
            price = 20
        ),
        OrbTemplate(
            EnumCurrencyOrb.ORB_OF_ALTERATION,
            "Orb of Alteration",
            "Перекатывает аффиксы магического предмета",
            price = 40
        ),
        OrbTemplate(
            EnumCurrencyOrb.ORB_OF_ALCHEMY,
            "Orb of Alchemy",
            "Обычный предмет становится редким и получает аффиксы",
            price = 120
        ),
        OrbTemplate(
            EnumCurrencyOrb.REGAL_ORB,
            "Regal Orb",
            "Магический предмет становится редким, сохраняя аффиксы и получая ещё один",
            price = 350
        ),
        OrbTemplate(
            EnumCurrencyOrb.CHAOS_ORB,
            "Chaos Orb",
            "Перекатывает аффиксы редкого предмета",
            price = 300
        ),
        OrbTemplate(
            EnumCurrencyOrb.EXALTED_ORB,
            "Exalted Orb",
            "Добавляет редкому предмету ещё один аффикс",
            price = 25000
        ),
        OrbTemplate(
            EnumCurrencyOrb.DIVINE_ORB,
            "Divine Orb",
            "Перекатывает значения аффиксов, сохраняя сами аффиксы и их тиры",
            price = 6000
        ),
        OrbTemplate(
            EnumCurrencyOrb.ORB_OF_ANNULMENT,
            "Orb of Annulment",
            "Убирает с предмета случайный аффикс",
            price = 4000
        ),
        OrbTemplate(
            EnumCurrencyOrb.ORB_OF_SCOURING,
            "Orb of Scouring",
            "Снимает все аффиксы и возвращает предмет к обычной редкости",
            price = 200
        ),
        OrbTemplate(
            EnumCurrencyOrb.BLESSED_ORB,
            "Blessed Orb",
            "Перекатывает значения implicit-модификаторов",
            price = 500
        ),
        OrbTemplate(
            EnumCurrencyOrb.VAAL_ORB,
            "Vaal Orb",
            "Портит предмет: вешает модификатор порчи, после чего предмет неизменяем",
            price = 800
        ),
        OrbTemplate(
            EnumCurrencyOrb.ORB_OF_CHANCE,
            "Orb of Chance",
            "Делает из обычного предмета предмет случайной редкости, изредка - уникальный",
            price = 60
        ),
        OrbTemplate(
            EnumCurrencyOrb.MIRROR_OF_KALANDRA,
            "Mirror of Kalandra",
            "Создаёт неизменяемую копию предмета",
            price = 10_000_000
        ),
    )

    /**
     * Документы сфер для коллекции `Items`.
     *
     * _id выводится из кода сферы и стабилен, поэтому пересев валюты
     * не ломает ссылки из инвентарей персонажей.
     */
    fun seed(): List<Items> = templates.map { template ->
        Items(
            name = template.name,
            category = EnumCurrencyOrb.CATEGORY,
            subCategory = template.orb.name,
            description = template.description,
            price = template.price,
            _id = template.orb.name.toStableObjectId()
        )
    }
}
