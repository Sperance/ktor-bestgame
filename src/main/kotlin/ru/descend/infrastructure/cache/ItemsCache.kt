package ru.descend.infrastructure.cache

import ru.descend.features.items.model.Items
import ru.descend.features.items.persistence.ItemsRepository

class ItemsCache(repository: ItemsRepository) : MongoCache<Items, ItemsRepository>(repository)
