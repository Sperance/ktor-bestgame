package server.routings

import application.data.characters.SnapshotCharacter
import application.data.users.DAOusers
import application.data.users.SnapshotUser
import extensions.RequestParams
import extensions.respond
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.Application
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer

/**
 * API для работы с таблицей [application.data.users.UserEntity] - список пользователей
 */
@OptIn(ExperimentalKtorApi::class, InternalSerializationApi::class)
fun Application.configureRoutingUser() {
    routing {

        route("/user") {

            val userDao = DAOusers()

            get("/all") {
                call.respond(userDao.getAll(call))
            }.describe {
                summary = "Получение списка всех пользователей"
                description = "Даже удаленных"
                operationId = "userAll"
                responses {
                    HttpStatusCode.OK {
                        description = "Список пользователей в формате JSON"
                        schema = jsonSchema<List<SnapshotUser>>()
                    }
                }
            }

            get("/{id}") {
                call.respond(userDao.getFromId(call))
            }.describe {
                summary = "Получение пользователя по идентификатору"
                description = "Получение пользователя по идентификатору (id)"
                operationId = "userById"
                parameters {
                    path("id") {
                        description = "id пользователя"
                        required = true
                    }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Объект пользователя в формате JSON"
                        schema = jsonSchema<SnapshotUser>()
                    }
                }
            }

            get("/{id}/characters") {
                call.respond(userDao.getCharacters(call))
            }.describe {
                summary = "Получение списка персонажей пользователя по его идентификатору"
                description = "Результат может быть пуст - в таком случае у пользователя не заведено ни одного персонажа"
                operationId = "userGetCharacters"
                parameters {
                    path("id") {
                        description = "id пользователя"
                        required = true
                    }
                }
                responses {
                    HttpStatusCode.OK {
                        description = "Список персонажей пользователя в формате JSON"
                        schema = jsonSchema<SnapshotCharacter>()
                    }
                    HttpStatusCode.BadRequest {
                        description = "Не указан обязательный параметр id"
                    }
                    HttpStatusCode.NotFound {
                        description = "Пользователь с указанным id не найден"
                    }
                }
            }

            /**
             * Получение персонажа пользователя по его идентификатору
             */
            get("/{id}/characters/{charId}") {
                call.respond(userDao.getCharactersById(call))
            }.describe {
                summary = "Получение персонажа у конкретного пользователя"
                operationId = "userCharacterById"
                parameters {
                    path("id") {
                        description = "id пользователя"
                        required = true
                    }
                    path("charId") {
                        description = "id персонажа"
                        required = true
                    }
                }
            }

            get("/{id}/testupdate") {
                call.respond(userDao.testUserUpdate(call))
            }

            post {
                call.respond(userDao.post(call, ListSerializer(SnapshotUser.serializer())))
            }

        }.describe {
            tag("user")
        }
    }
}
