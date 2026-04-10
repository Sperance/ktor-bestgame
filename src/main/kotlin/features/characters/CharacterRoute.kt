package features.characters

import base.route.BaseRoute

class CharacterRoute(
    characterService: CharacterService = CharacterService()
) : BaseRoute<Character, CharacterTable>(
    service = characterService,
    basePath = "/api/character",
    entitySerializer = Character.serializer()   // ← явный сериализатор
)