package features.caches

import features.data.items.Items
import features.data.items.ItemsRepository

object ItemsCache : MongoCache<Items, ItemsRepository>()