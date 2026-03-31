package extensions

sealed class ResultResponse {
    class Success(val data: Any?, val headers: Map<String, Any?>? = null) : ResultResponse()
    class Error(val message: MutableMap<String, String>) : ResultResponse()
}