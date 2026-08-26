package base.exception.model

import base.exception.BaseException

object RecipeExceptions {
    open class RecipeException(message: String?, errorMethod: String?, errorCode: String) : BaseException(message, "Recipe", errorMethod, errorCode) {
        override fun toString(): String {
            return "{RecipeException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = RecipeException(value, errorMethod, "RC_001")
    fun funExceptionItemInNull(errorMethod: String, value: String? = "") = RecipeException("Request have a NULL IN item value", errorMethod, "RC_002")
    fun funExceptionItemOutNull(errorMethod: String, value: String? = "") = RecipeException("Request have a NULL OUT item value", errorMethod, "RC_003")
    fun funExceptionItemNotFound(errorMethod: String, value: String? = "") = RecipeException("Not found item with ID: $value", errorMethod, "RC_004")
    fun funExceptionDuplicateName(errorMethod: String, value: String? = "") = RecipeException("Recipe with name '$value' already exists", errorMethod, "RC_005")
    fun funExceptionOutIncorrect(errorMethod: String, value: String? = "") = RecipeException("Out items are incorrect", errorMethod, "RC_006")
}