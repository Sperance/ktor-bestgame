package ru.descend.features.passives.persistence

import ru.descend.infrastructure.mongo.BaseRepository

class PassiveReceiptRepository : BaseRepository<PassiveReceipt>(PassiveReceipt::class) {
    init { initialize() }
}
