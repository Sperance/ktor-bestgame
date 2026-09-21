package features.data.auction

import application.enums.EnumAuctionLotKind
import application.enums.EnumAuctionLotStatus
import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import com.mongodb.client.model.Filters
import kotlinx.serialization.Serializable
import org.bson.conversions.Bson

/**
 * Фильтр витрины аукциона.
 *
 * Пустой фильтр - это вся витрина: закрытые лоты не показываются никогда,
 * поэтому условие по статусу стоит всегда.
 *
 * Все поля снимаются с лота, а не из справочников, поэтому поиск
 * укладывается в один запрос к Mongo, см. [AuctionLot].
 */
@Serializable
data class AuctionSearch(

    /**
     * Экипировка или простые предметы.
     */
    val kind: EnumAuctionLotKind? = null,

    /**
     * Коды предметов, подходящих под поисковый текст.
     *
     * Названий в лотах нет, поэтому текст игрока превращает в коды
     * тот, кто держит словари локализации, - см. AuctionLotRepository.
     * Пустой список означает "ничего не нашлось" и отсекает всю витрину.
     */
    val itemCodes: List<String>? = null,

    val slot: EnumEquipmentType? = null,

    val rarity: EnumRarity? = null,

    val minItemLevel: Int? = null,

    val maxItemLevel: Int? = null,

    /**
     * Сфера, в которой назначена цена - ссылка на `Items._id`.
     */
    val priceOrbId: String? = null,

    /**
     * Верхняя граница цены. Вместе с [priceOrbId] это "не дороже N таких сфер".
     */
    val maxPrice: Long? = null,

    /**
     * Лоты конкретного продавца.
     */
    val sellerId: String? = null,

    /**
     * Спрятать лоты этого персонажа - чтобы не видеть в витрине свои.
     */
    val excludeSellerId: String? = null,
) {

    /**
     * Условие выборки для Mongo.
     *
     * Перечисления хранятся строками, поэтому в фильтр идёт `name`,
     * а не сам элемент перечисления.
     */
    fun toFilter(): Bson {
        val conditions = mutableListOf<Bson>(
            Filters.eq("status", EnumAuctionLotStatus.ACTIVE.name)
        )

        kind?.let { conditions.add(Filters.eq("kind", it.name)) }
        slot?.let { conditions.add(Filters.eq("slot", it.name)) }
        rarity?.let { conditions.add(Filters.eq("rarity", it.name)) }
        priceOrbId?.let { conditions.add(Filters.eq("priceOrbId", it)) }
        sellerId?.let { conditions.add(Filters.eq("sellerId", it)) }
        excludeSellerId?.let { conditions.add(Filters.ne("sellerId", it)) }

        maxPrice?.let { conditions.add(Filters.lte("price", it)) }
        minItemLevel?.let { conditions.add(Filters.gte("itemLevel", it)) }
        maxItemLevel?.let { conditions.add(Filters.lte("itemLevel", it)) }

        // Поиск по названию пришёл уже разрешённым в коды
        itemCodes?.let { conditions.add(Filters.`in`("itemCode", it)) }

        return Filters.and(conditions)
    }
}
