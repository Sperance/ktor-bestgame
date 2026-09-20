package base.exception.model

import base.exception.BaseException

object AuctionExceptions {
    open class AuctionException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Auction", errorMethod, errorCode) {
        override fun toString(): String {
            return "{AuctionException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = AuctionException(value, errorMethod, "AU_001")
    fun funExceptionLevel(errorMethod: String, value: String? = "") = AuctionException("Character level $value is too low for the auction", errorMethod, "AU_002")
    fun funExceptionLotNotFound(errorMethod: String, value: String? = "") = AuctionException("Auction lot $value not found", errorMethod, "AU_003")
    fun funExceptionLotClosed(errorMethod: String, value: String? = "") = AuctionException("Auction lot $value is not on sale anymore", errorMethod, "AU_004")
    fun funExceptionNotSeller(errorMethod: String, value: String? = "") = AuctionException("Auction lot $value belongs to another character", errorMethod, "AU_005")
    fun funExceptionOwnLot(errorMethod: String, value: String? = "") = AuctionException("Character cannot buy its own lot $value", errorMethod, "AU_006")
    fun funExceptionPriceNotOrb(errorMethod: String, value: String? = "") = AuctionException("Price must be set in currency orbs, $value is not an orb", errorMethod, "AU_007")
    fun funExceptionPrice(errorMethod: String, value: String? = "") = AuctionException("Auction price $value must be positive", errorMethod, "AU_008")
    fun funExceptionAmount(errorMethod: String, value: String? = "") = AuctionException("Auction amount $value must be positive", errorMethod, "AU_009")
    fun funExceptionItemEquipped(errorMethod: String, value: String? = "") = AuctionException("Item $value must be unequipped before it goes on sale", errorMethod, "AU_010")
    fun funExceptionLotBroken(errorMethod: String, value: String? = "") = AuctionException("Auction lot $value carries no goods", errorMethod, "AU_011")
}
