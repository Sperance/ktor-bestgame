package features.characterMongo

import base.route.BaseRouteMongo

class CharacterMongoRoute : BaseRouteMongo<CharacterMongo, CharacterMongo>(
    repository = CharacterMongoRepository,
    basePath = "/api/characters_mongo",
    entitySerializer = CharacterMongo.serializer(),
    responseSerializer = CharacterMongo.serializer(),
    toResponse = { it }
)