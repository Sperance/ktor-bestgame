package features.data.auction

import application.enums.EnumAuctionLotKind
import application.enums.EnumAuctionLotStatus
import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.entity.VersionedEntity
import extensions.now
import features.data.character.Character
import features.data.equipment.equipment_data.Equipment
import features.data.inventory.CharacterEquipment
import features.data.items.Items
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

/**
 * Лот игрового аукциона. Отдельная коллекция Mongo `AuctionLot`.
 *
 * Пока лот на витрине, товар лежит внутри него, а не у продавца: экземпляр
 * экипировки уходит из коллекции `CharacterEquipment` прямо в лот, простые
 * предметы списываются со склада персонажа. Так один и тот же предмет нельзя
 * ни надеть, ни продать дважды, пока он выставлен.
 *
 * Цена назначается только в валютных сферах: [priceOrbId] ссылается
 * на предмет категории `CURRENCY` в справочнике `Items`.
 *
 * Поля витрины ([title], [slot], [rarity], [itemLevel]) - снимок предмета
 * на момент выставления. Они лежат прямо здесь, чтобы фильтр аукциона
 * работал одним запросом к Mongo, без похода в справочники.
 */
@Serializable
data class AuctionLot(

    /**
     * Продавец - ссылка на `Character._id`.
     */
    var sellerId: String,

    /**
     * Снимок имени продавца: витрина читается без обращения к персонажам.
     */
    var sellerName: String = "",

    /**
     * Что продаётся: экземпляр экипировки или стакающиеся предметы.
     */
    var kind: EnumAuctionLotKind = EnumAuctionLotKind.EQUIPMENT,

    /**
     * Экземпляр экипировки, снятый с продавца. Заполнен только у [EnumAuctionLotKind.EQUIPMENT].
     *
     * Внутри лота у предмета нет владельца: `characterId` проставляется
     * заново, когда лот уходит покупателю или возвращается продавцу.
     */
    var equipment: CharacterEquipment? = null,

    /**
     * Предмет справочника `Items`. Заполнен только у [EnumAuctionLotKind.ITEM].
     */
    var itemId: String = "",

    /**
     * Сколько предметов в лоте. У экипировки всегда один.
     */
    var amount: Long = 1,

    /**
     * Сфера, в которой назначена цена - ссылка на `Items._id`.
     */
    var priceOrbId: String = "",

    /**
     * Сколько сфер стоит лот целиком.
     */
    var price: Long = 0,

    // ==================== Снимок для витрины и фильтра ====================

    /**
     * Название предмета.
     */
    var title: String = "",

    /**
     * Слот экипировки. null у простых предметов.
     */
    var slot: EnumEquipmentType? = null,

    /**
     * Редкость конкретного экземпляра - её могли изменить сферы,
     * поэтому берётся с предмета, а не с шаблона. null у простых предметов.
     */
    var rarity: EnumRarity? = null,

    /**
     * Уровень предмета. Ноль у простых предметов.
     */
    var itemLevel: Int = 0,

    // ==================== Состояние торгов ====================

    var status: EnumAuctionLotStatus = EnumAuctionLotStatus.ACTIVE,

    /**
     * Покупатель - ссылка на `Character._id`. Заполняется при продаже.
     */
    var buyerId: String? = null,

    /**
     * Когда лот ушёл с витрины - продан или снят.
     */
    var closedAt: LocalDateTime? = null,

    override var _id: String = ObjectId().toHexString(),
    override var version: Long = 0,
    override var deleted: Boolean = false,
    override val createdAt: LocalDateTime = LocalDateTime.now(),
    override var updatedAt: LocalDateTime = LocalDateTime.now(),
) : VersionedEntity {

    fun isOnSale(): Boolean = status == EnumAuctionLotStatus.ACTIVE

    companion object {

        /**
         * Лот с экземпляром экипировки.
         *
         * @param item предмет, уже снятый с инвентаря продавца
         * @param template шаблон предмета - из него берутся слот и уровень
         */
        fun forEquipment(
            seller: Character,
            item: CharacterEquipment,
            template: Equipment,
            priceOrbId: String,
            price: Long,
        ): AuctionLot {
            // Пока лот на витрине, у предмета нет владельца
            item.characterId = ""
            item.equippedSlot = null

            return AuctionLot(
                sellerId = seller._id,
                sellerName = seller.name,
                kind = EnumAuctionLotKind.EQUIPMENT,
                equipment = item,
                amount = 1,
                priceOrbId = priceOrbId,
                price = price,
                title = template.name,
                slot = template.slot,
                rarity = item.rarity,
                itemLevel = template.itemLevel
            )
        }

        /**
         * Лот со стакающимися предметами.
         */
        fun forItem(
            seller: Character,
            item: Items,
            amount: Long,
            priceOrbId: String,
            price: Long,
        ): AuctionLot = AuctionLot(
            sellerId = seller._id,
            sellerName = seller.name,
            kind = EnumAuctionLotKind.ITEM,
            itemId = item._id,
            amount = amount,
            priceOrbId = priceOrbId,
            price = price,
            title = item.name
        )
    }
}
