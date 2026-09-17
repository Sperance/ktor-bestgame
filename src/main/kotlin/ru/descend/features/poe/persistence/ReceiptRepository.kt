package ru.descend.features.poe.persistence

import ru.descend.infrastructure.mongo.BaseRepository

class ReceiptRepository : BaseRepository<PoeReceipt>(PoeReceipt::class)
