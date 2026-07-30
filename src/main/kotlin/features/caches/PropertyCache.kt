package features.caches

import features.data.property.Property
import features.data.property.PropertyRepository

class PropertyCache(repository: PropertyRepository) : MongoCache<Property, PropertyRepository>(repository)