package features.caches

import features.data.items.Items
import features.data.items.ItemsRepository

class ItemsCache(repository: ItemsRepository) : MongoCache<Items, ItemsRepository>(repository)