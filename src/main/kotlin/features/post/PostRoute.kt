package features.post

import base.route.BaseRoute
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

class PostRoute(
    private val postService: PostService = PostService()
) : BaseRoute<Post, PostsTable>(
    service = postService,
    basePath = "/api/posts",
    entitySerializer = Post.serializer()   // ← явный сериализатор
) {
    override fun additionalRoutes(route: Route) = with(route) {

        get("/published") {
            val posts = postService.findPublished()
            call.respondEntityList(posts)
        }

        get("/by-author/{authorId}") {
            val authorId = call.longParam("authorId")
            val posts = postService.findByAuthor(authorId)
            call.respondEntityList(posts)
        }
    }
}