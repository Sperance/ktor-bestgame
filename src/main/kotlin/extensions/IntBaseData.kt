package extensions

import application.DatabaseConfig.dbQuery
import application.data.BaseDTO
import application.data.BaseEntity
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.toByteArray
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import server.enums.EnumDataFilter
import server.enums.EnumSQLTypes
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.reflect.KMutableProperty0

sealed class ResultResponse {
    class Success(val data: Any?, val headers: Map<String, Any?>? = null) : ResultResponse()
    class Error(val message: MutableMap<String, String>) : ResultResponse()
}

@Suppress("UNCHECKED_CAST")
abstract class IntBaseDataImpl<T> {

    abstract fun isValidLine(): Boolean

    abstract fun getData(): List<T>
    abstract fun getDataPagination(page: Int): List<T>
    abstract fun getDataFromId(id: Long?): T?

    open suspend fun get(call: ApplicationCall): ResultResponse {
        return try {
            val page = call.parameters["page"]?.toIntOrNull()
            if (page == null) {
                ResultResponse.Success(getData())
            } else {
                ResultResponse.Success(getDataPagination(page = page))
            }
        } catch (e: Exception) {
            ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
        }
    }

    open suspend fun getInvalid(call: ApplicationCall): ResultResponse {
        return try {
            ResultResponse.Success(getData())
        } catch (e: Exception) {
            ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
        }
    }

    open suspend fun getFromId(call: ApplicationCall, params: RequestParams<T>): ResultResponse {
        try {
            val id = call.parameters["id"]
            if (id.isNullOrEmpty() || !id.toIntPossible()) {
                return ResultResponse.Error(generateMapError(call, 301 to "Incorrect parameter 'id'. This parameter must be 'Int' type"))
            }
            params.checkings.forEach { check ->
                val res = check.invoke(this as T)
                if (res.result) {
                    return ResultResponse.Error(generateMapError(call, res.errorCode to res.errorText))
                }
            }
            params.defaults.forEach { def ->
                val res = def.invoke(this as T)
                val property = res.first as KMutableProperty0<Any?>
                if (!property.get().isAllNullOrEmpty()) return@forEach
                property.set(res.second)
            }
            val data = getDataFromId(id.toLongOrNull())
            if (data == null) {
                return ResultResponse.Error(generateMapError(call, 302 to "Не найдена запись ${this::class.simpleName} с id $id"))
            }
            return ResultResponse.Success(data)
        } catch (e: Exception) {
            return ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
        }
    }

    open suspend fun delete(call: ApplicationCall, params: RequestParams<T>): ResultResponse {
        return dbQuery { tx ->
            try {
                val id = call.parameters["id"]
                if (id == null || !id.toIntPossible()) {
                    tx.rollback()
                    return@dbQuery ResultResponse.Error(generateMapError(call, 301 to "Incorrect parameter 'id'($id). This parameter must be 'Int' type"))
                }

                params.checkings.forEach { check ->
                    val res = check.invoke(this as T)
                    if (res.result) {
                        tx.rollback()
                        return@dbQuery ResultResponse.Error(generateMapError(call, res.errorCode to res.errorText))
                    }
                }

                params.defaults.forEach { def ->
                    val res = def.invoke(this as T)
                    val property = res.first as KMutableProperty0<Any?>
                    if (!property.get().isAllNullOrEmpty()) return@forEach
                    val value = res.second
                    property.set(value)
                }

                val currectObjClassName = this::class.simpleName!!
//                val tblObj = getField("tbl_${currectObjClassName.lowercase()}") as EntityMetamodel<*, *, *>
//                val auProp = tblObj.getAutoIncrementProperty() as PropertyMetamodel<Any, Int, Int>
//                val findedObj = getDataOne({ auProp eq id.toInt() })
//                if (findedObj == null) {
//                    tx.rollback()
//                    return@dbQuery ResultResponse.Error(generateMapError(call, 302 to "Not found $currectObjClassName with id $id"))
//                }

//                params.onBeforeCompleted?.invoke(findedObj as T)

                ResultResponse.Success("$currectObjClassName with id $id successfully deleted")
            } catch (e: Exception) {
                tx.rollback()
                return@dbQuery ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
            }
        }
    }

    open suspend fun deleteSafe(call: ApplicationCall, params: RequestParams<T>): ResultResponse {
        return dbQuery { tx ->
            try {
                val id = call.parameters["id"]
                if (id == null || !id.toIntPossible()) {
                    tx.rollback()
                    return@dbQuery ResultResponse.Error(generateMapError(call, 301 to "Incorrect parameter 'id'($id). This parameter must be 'Int' type"))
                }

                params.checkings.forEach { check ->
                    val res = check.invoke(this as T)
                    if (res.result) {
                        tx.rollback()
                        return@dbQuery ResultResponse.Error(generateMapError(call, res.errorCode to res.errorText))
                    }
                }

                params.defaults.forEach { def ->
                    val res = def.invoke(this as T)
                    val property = res.first as KMutableProperty0<Any?>
                    if (!property.get().isAllNullOrEmpty()) return@forEach
                    val value = res.second
                    property.set(value)
                }

                val currectObjClassName = this::class.simpleName!!
//                val tblObj = getField("tbl_${currectObjClassName.lowercase()}") as EntityMetamodel<*, *, *>
//                val auProp = tblObj.getAutoIncrementProperty() as PropertyMetamodel<Any, Int, Int>
//                val findedObj = getDataOne({ auProp eq id.toInt() })
//                if (findedObj == null) {
//                    tx.rollback()
//                    return@dbQuery ResultResponse.Error(generateMapError(call, 302 to "Not found $currectObjClassName with id $id"))
//                }

//                params.onBeforeCompleted?.invoke(findedObj as T)
//
//                findedObj.deleteSafe()

                ResultResponse.Success("$currectObjClassName with id $id successfully safe deleted")
            } catch (e: Exception) {
                tx.rollback()
                return@dbQuery ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
            }
        }
    }

    open suspend fun restore(call: ApplicationCall, params: RequestParams<T>): ResultResponse {
        return dbQuery { tx ->
            try {
                val id = call.parameters["id"]
                if (id == null || !id.toIntPossible()) {
                    tx.rollback()
                    return@dbQuery ResultResponse.Error(generateMapError(call, 301 to "Incorrect parameter 'id'($id). This parameter must be 'Int' type"))
                }

                params.checkings.forEach { check ->
                    val res = check.invoke(this as T)
                    if (res.result) {
                        tx.rollback()
                        return@dbQuery ResultResponse.Error(generateMapError(call, res.errorCode to res.errorText))
                    }
                }

                params.defaults.forEach { def ->
                    val res = def.invoke(this as T)
                    val property = res.first as KMutableProperty0<Any?>
                    if (!property.get().isAllNullOrEmpty()) return@forEach
                    val value = res.second
                    property.set(value)
                }

                val currectObjClassName = this::class.simpleName!!
//                val tblObj = getField("tbl_${currectObjClassName.lowercase()}") as EntityMetamodel<*, *, *>
//                val auProp = tblObj.getAutoIncrementProperty() as PropertyMetamodel<Any, Int, Int>
//                val findedObj = getDataOne({ auProp eq id.toInt() })
//                if (findedObj == null) {
//                    tx.rollback()
//                    return@dbQuery ResultResponse.Error(generateMapError(call, 302 to "Not found $currectObjClassName with id $id"))
//                }
//
//                params.onBeforeCompleted?.invoke(findedObj as T)
//
//                findedObj.restoreSafe()

                ResultResponse.Success("$currectObjClassName with id $id successfully safe restored")
            } catch (e: Exception) {
                tx.rollback()
                return@dbQuery ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
            }
        }
    }

    open suspend fun update(call: ApplicationCall, params: RequestParams<T>, serializer: KSerializer<T>): ResultResponse {
        return dbQuery { tx ->
            try {
                val multipartData = call.receiveMultipart()

                var newObject: T? = null
                val currectObjClassName = this::class.simpleName!!
                var fileName: String? = null
                var fileBytes: ByteArray? = null
                var countFiles = 0

                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            newObject = Json.decodeFromString(serializer, part.value)
                        }

                        is PartData.FileItem -> {
                            countFiles++
                            fileName = part.originalFileName
                            fileBytes = part.provider().toByteArray()
                        }

                        is PartData.BinaryChannelItem -> {}
                        is PartData.BinaryItem -> {}
                    }
                }
                if (countFiles > 1) {
                    tx.rollback()
                    return@dbQuery ResultResponse.Error(generateMapError(call, 301 to "Обнаружено несколько файлов($countFiles). Сервер не поддерживает обработку более 1 файла за раз"))
                }
                if (params.isNeedFile && fileBytes == null) {
                    tx.rollback()
                    return@dbQuery ResultResponse.Error(generateMapError(call, 302 to "Для объекта $currectObjClassName ожидался файл, который не был получен"))
                }
                if (newObject == null) {
                    tx.rollback()
                    return@dbQuery ResultResponse.Error(generateMapError(call, 303 to "Не удалось создать объект $currectObjClassName по входящему JSON"))
                }
                params.checkings.forEach { check ->
                    val res = check.invoke(newObject as T)
                    if (res.result) {
                        tx.rollback()
                        return@dbQuery ResultResponse.Error(generateMapError(call, res.errorCode to res.errorText))
                    }
                }
                params.defaults.forEach { def ->
                    val res = def.invoke(newObject as T)
                    val property = res.first as KMutableProperty0<Any?>
                    if (!property.get().isAllNullOrEmpty()) return@forEach
                    val value = res.second
                    property.set(value)
                }
//                val findedObj = getDataFromId(newObject?.id)
//                if (findedObj == null) {
//                    tx.rollback()
//                    return@dbQuery ResultResponse.Error(generateMapError(call, 304 to "Not found $currectObjClassName with id ${newObject?.id}"))
//                }

//                params.checkOnUpdate?.invoke(findedObj, newObject as T)

                params.onBeforeCompleted?.invoke(newObject as T)
//                findedObj.updateFromNullable(newObject)
//
//                val updated = findedObj.update("IntBaseData::update")

                return@dbQuery ResultResponse.Success(true)
//                return@dbQuery ResultResponse.Success(updated)
            } catch (e: Exception) {
                tx.rollback()
                e.printStackTrace()
                return@dbQuery ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
            }
        }
    }

    open suspend fun post(call: ApplicationCall, params: RequestParams<T>, serializer: KSerializer<List<T>>): ResultResponse {
        return dbQuery { tx ->
            try {
                val multipartData = call.receiveMultipart()

                var finishObject: T? = null
                var jsonString = ""
                val currectObjClassName = this::class.simpleName!!

                var fileName: String? = null
                var fileBytes: ByteArray? = null

                multipartData.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            jsonString = part.value
                        }

                        is PartData.FileItem -> {
                            fileName = part.originalFileName
                            fileBytes = part.provider().toByteArray()
                        }

                        else -> {
                            printLog("Unknown part type: ${part::class.simpleName}")
                        }
                    }
                }

                val newObject = Json.decodeFromString(serializer, jsonString)

                if (params.isNeedFile && fileBytes == null) {
                    tx.rollback()
                    return@dbQuery ResultResponse.Error(generateMapError(call, 301 to "Для объекта $currectObjClassName ожидался файл, который не был получен"))
                }

                params.checkings.forEach { check ->
                    newObject.forEach { item ->
                        val res = check.invoke(item)
                        if (res.result) {
                            tx.rollback()
                            return@dbQuery ResultResponse.Error(generateMapError(call, res.errorCode to res.errorText))
                        }
                    }
                }

                params.defaults.forEach { def ->
                    newObject.forEach { item ->
                        val res = def.invoke(item)
                        val property = res.first as KMutableProperty0<Any?>
                        if (property.get().isAllNullOrEmpty()) {
                            property.set(res.second)
                        }
                    }
                }

                if (newObject.size > 1 && fileBytes != null) {
                    tx.rollback()
                    return@dbQuery ResultResponse.Error(generateMapError(call, 302 to "Невозможно сохранить файл изображения к массиву элементов (${newObject.size})"))
                }

                newObject.forEach { item ->
                    params.onBeforeCompleted?.invoke(item)
//                    finishObject = item.create("IntBaseDataImpl::post")
                }

                return@dbQuery ResultResponse.Success(true)
//                return@dbQuery ResultResponse.Success(finishObject as Any)
            } catch (e: Exception) {
                tx.rollback()
                return@dbQuery ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
            }
        }
    }
}