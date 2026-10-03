package features.logic.trade

import extensions.printLog
import features.data.auction.AuctionLotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * Срок лотов (1.53.0; письма за сутки и возврат почтой - 1.74.0): раз в минуту просроченные лоты снимаются пачкой по [BATCH], каждый - под очередью своего
 * продавца. Прежде это делал каждый поиск по аукциону: чтение писало чужих героев мимо их очереди, и продавец
 * ловил гонку версий на своей команде.
 */
class AuctionExpiry(private val lots: AuctionLotRepository) {
    fun start(scope: CoroutineScope) = scope.launch {
        while (isActive) {
            delay(1.minutes)
            runCatching { lots.expireDue(BATCH) }.onFailure { printLog("[AuctionExpiry] ${it.message}", true) }
            runCatching { lots.warnDue(BATCH) }.onFailure { printLog("[AuctionExpiry] warn: ${it.message}", true) }
        }
    }

    private companion object {
        const val BATCH = 50
    }
}
