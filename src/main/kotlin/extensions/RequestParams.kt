package extensions

import kotlin.reflect.KMutableProperty0

class RequestParams<T> {
    var isNeedFile = false
    val checkings: ArrayList<suspend (T) -> CheckObj> = ArrayList()
    val defaults: ArrayList<suspend (T) -> Pair<KMutableProperty0<*>, Any?>> = ArrayList()
    var onBeforeCompleted: (suspend (T) -> Any)? = null
    var checkOnUpdate: (suspend (T, T) -> Any)? = null
}

data class CheckObj(val result: Boolean, val errorCode: Int, val errorText: String)