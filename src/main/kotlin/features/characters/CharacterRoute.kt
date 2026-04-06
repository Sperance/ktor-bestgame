package features.characters

import base.route.BaseRoute
import features.post.Post
import features.post.PostService
import features.post.PostsTable
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class CharacterRoute(
    private val characterService: CharacterService = CharacterService()
) : BaseRoute<Character, CharacterTable>(
    service = characterService,
    basePath = "/api/character",
    entitySerializer = Character.serializer()   // ← явный сериализатор
)