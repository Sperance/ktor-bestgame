package base.exception.model

import base.exception.BaseException

object RecipeExceptions {
    open class RecipeException(message: String?, errorMethod: String?, errorCode: String, messageArgs: List<String> = emptyList()) : BaseException(message, "Recipe", errorMethod, errorCode, messageArgs) {
        override fun toString(): String {
            return "{RecipeException} message = $message, errorMethod = $errorMethod, errorCode = $errorCode, errorClass = $errorClass"
        }
    }

    fun funException(errorMethod: String, value: String? = "") = RecipeException(value, errorMethod, "RC_001", listOf(value.orEmpty()))
    fun funExceptionItemInNull(errorMethod: String, value: String? = "") = RecipeException("Request have a NULL IN item value", errorMethod, "RC_002", listOf(value.orEmpty()))
    fun funExceptionItemOutNull(errorMethod: String, value: String? = "") = RecipeException("Request have a NULL OUT item value", errorMethod, "RC_003", listOf(value.orEmpty()))
    fun funExceptionItemNotFound(errorMethod: String, value: String? = "") = RecipeException("Not found item with: $value", errorMethod, "RC_004", listOf(value.orEmpty()))
    fun funExceptionDuplicateName(errorMethod: String, value: String? = "") = RecipeException("Recipe with name '$value' already exists", errorMethod, "RC_005", listOf(value.orEmpty()))
    fun funExceptionOutIncorrect(errorMethod: String, value: String? = "") = RecipeException("Out items are incorrect", errorMethod, "RC_006", listOf(value.orEmpty()))
    fun funExceptionInMany(errorMethod: String, value: String? = "") = RecipeException("Recipe IN params must be only one not-null value", errorMethod, "RC_007", listOf(value.orEmpty()))
    fun funExceptionRecipeNotFound(errorMethod: String, value: String? = "") = RecipeException("Recipe with id $value not found", errorMethod, "RC_008", listOf(value.orEmpty()))
    fun funExceptionRecipeNotAllowed(errorMethod: String, value: String? = "") = RecipeException("Recipe with id $value not allowed to Character", errorMethod, "RC_009", listOf(value.orEmpty()))
    fun funExceptionDuplicateInArrayIn(errorMethod: String, value: String? = "") = RecipeException("Duplicate params in IN array: $value", errorMethod, "RC_010", listOf(value.orEmpty()))
    fun funExceptionManyUses(errorMethod: String, value: String? = "") = RecipeException("Field 'globalUses' must be 0/ Now: $value", errorMethod, "RC_011", listOf(value.orEmpty()))
}