package features.character

import base.route.BaseRoute

class CharacterRoute(
    repo: CharacterRepository
) : BaseRoute<Character, Character>(
    repository = repo,
    entitySerializer = Character.serializer(),
    responseSerializer = Character.serializer(),
    toResponse = { it }
)