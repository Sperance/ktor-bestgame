package features.post

import base.model.ApiResponse
import base.model.apiResponseListSerializer
import base.route.BaseRoute
import base.route.respond
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class PostRoute(
    private val postService: PostService = PostService()
) : BaseRoute<Post, PostsTable>(
    service = postService,
    basePath = "/api/posts",
    entitySerializer = Post.serializer()   // ← явный сериализатор
) {

    // Сериализатор для нестандартного PostWithAuthor
    private val postWithAuthorListSerializer = apiResponseListSerializer(PostWithAuthor.serializer())

    override fun additionalRoutes(route: Route) = with(route) {

        get("/published") {
            val posts = postService.findPublished()
            call.respondEntityList(posts)
        }

        get("/published/with-authors") {
            val posts = postService.findPublishedWithAuthors()
            val response = ApiResponse.ok(posts)
            call.respond(HttpStatusCode.OK, postWithAuthorListSerializer, response)
        }

        get("/by-author/{authorId}") {
            val authorId = call.longParam("authorId")
            val posts = postService.findByAuthor(authorId)
            call.respondEntityList(posts)
        }
    }
}