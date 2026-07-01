package features.character

import base.route.BaseRoute

class CharacterRoute : BaseRoute<Character, Character>(
    repository = CharacterRepository,
    entitySerializer = Character.serializer(),
    responseSerializer = Character.serializer(),
    toResponse = { it }
)