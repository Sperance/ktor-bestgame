package features.caches

import features.data.property.Property
import features.data.property.PropertyRepository

object PropertyCache : MongoCache<Property, PropertyRepository>()