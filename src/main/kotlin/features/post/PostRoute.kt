package features.post

import base.model.ApiResponse
import base.route.BaseRoute
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class PostRoute(
    private val postService: PostService = PostService()
) : BaseRoute<PostResponse, CreatePostRequest, UpdatePostRequest>(postService, "/api/posts") {

    override val entitySerializer: KSerializer<PostResponse> = PostResponse.serializer()

    override suspend fun deserializeCreate(call: ApplicationCall) =
        call.receive<CreatePostRequest>()

    override suspend fun deserializeUpdate(call: ApplicationCall) =
        call.receive<UpdatePostRequest>()

    override fun additionalRoutes(route: Route) = with(route) {

        get("/published") {
            call.respondJson(
                HttpStatusCode.OK,
                ApiResponse.serializer(ListSerializer(entitySerializer)),
                ApiResponse.ok(postService.findPublished())
            )
        }

        get("/published/with-authors") {
            call.respondJson(
                HttpStatusCode.OK,
                ApiResponse.serializer(ListSerializer(PostWithAuthorResponse.serializer())),
                ApiResponse.ok(postService.findPublishedWithAuthors())
            )
        }

        get("/by-author/{authorId}") {
            val authorId = call.longParam("authorId")
            call.respondJson(
                HttpStatusCode.OK,
                ApiResponse.serializer(ListSerializer(entitySerializer)),
                ApiResponse.ok(postService.findByAuthor(authorId))
            )
        }

        get("/search") {
            val title = call.queryParam("title")
            call.respondJson(
                HttpStatusCode.OK,
                ApiResponse.serializer(ListSerializer(entitySerializer)),
                ApiResponse.ok(postService.searchByTitle(title))
            )
        }

        get("/count-by-author/{authorId}") {
            val authorId = call.longParam("authorId")
            val ser = ApiResponse.serializer(
                MapSerializer(String.serializer(), Long.serializer())
            )
            call.respondJson(
                HttpStatusCode.OK,
                ser,
                ApiResponse.ok(mapOf("authorId" to authorId, "count" to postService.countByAuthor(authorId)))
            )
        }
    }
}