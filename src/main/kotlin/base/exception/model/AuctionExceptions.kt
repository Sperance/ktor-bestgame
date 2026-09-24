package base.exception.model

import base.exception.BaseException

object AuctionExceptions {
    open class AuctionException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Auction", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{AuctionException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = AuctionException(value, errorMethod, "AU_001", listOf(value.orEmpty()))
    fun funExceptionLevel(errorMethod: String, value: String? = "") = AuctionException("Character level $value is too low for the auction", errorMethod, "AU_002", listOf(value.orEmpty()))
    fun funExceptionLotNotFound(errorMethod: String, value: String? = "") = AuctionException("Auction lot $value not found", errorMethod, "AU_003", listOf(value.orEmpty()))
    fun funExceptionLotClosed(errorMethod: String, value: String? = "") = AuctionException("Auction lot $value is not on sale anymore", errorMethod, "AU_004", listOf(value.orEmpty()))
    fun funExceptionNotSeller(errorMethod: String, value: String? = "") = AuctionException("Auction lot $value belongs to another character", errorMethod, "AU_005", listOf(value.orEmpty()))
    fun funExceptionOwnLot(errorMethod: String, value: String? = "") = AuctionException("Character cannot buy its own lot $value", errorMethod, "AU_006", listOf(value.orEmpty()))
    fun funExceptionPriceNotOrb(errorMethod: String, value: String? = "") = AuctionException("Price must be set in currency orbs, $value is not an orb", errorMethod, "AU_007", listOf(value.orEmpty()))
    fun funExceptionPrice(errorMethod: String, value: String? = "") = AuctionException("Auction price $value must be positive", errorMethod, "AU_008", listOf(value.orEmpty()))
    fun funExceptionAmount(errorMethod: String, value: String? = "") = AuctionException("Auction amount $value must be positive", errorMethod, "AU_009", listOf(value.orEmpty()))
    fun funExceptionItemEquipped(errorMethod: String, value: String? = "") = AuctionException("Item $value must be unequipped before it goes on sale", errorMethod, "AU_010", listOf(value.orEmpty()))
    fun funExceptionLotLimit(errorMethod: String, value: String? = "") = AuctionException("All $value lot places are taken", errorMethod, "AU_012", listOf(value.orEmpty()))
    fun funExceptionSlotsMax(errorMethod: String, value: String? = "") = AuctionException("No more than $value lot places", errorMethod, "AU_013", listOf(value.orEmpty()))
    fun funExceptionLotBroken(errorMethod: String, value: String? = "") = AuctionException("Auction lot $value carries no goods", errorMethod, "AU_011", listOf(value.orEmpty()))
}
