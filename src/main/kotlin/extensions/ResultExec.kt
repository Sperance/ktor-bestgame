package extensions

sealed class ResultExec {
    class Success : ResultExec()
    class Error(val message: String) : ResultExec()

    // Вспомогательные методы
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error

    override fun toString(): String {
        return when (this) {
            is Success -> "ResultExec:Success"
            is Error   -> "ResultExec:Error [$message]"
        }
    }
}