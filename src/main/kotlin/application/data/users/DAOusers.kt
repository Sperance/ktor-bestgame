package application.data.users

import application.DatabaseConfig.dbQuery
import application.data.ExposedBaseDao
import extensions.ResultResponse
import extensions.generateMapError
import io.ktor.server.application.ApplicationCall

class DAOusers : ExposedBaseDao<UsersTable, UserEntity, SnapshotUser>(
    UsersTable,
    UserEntity
) {
    override fun mapDtoToEntity(dto: SnapshotUser, entity: UserEntity) {
        entity.name = dto._name
        entity.email = dto._email
    }

    suspend fun getCharacters(call: ApplicationCall): ResultResponse {
        return try {
            val id = call.parameters["id"]?.toLongOrNull() ?: return ResultResponse.Error(
                generateMapError(call, 301 to "Не указан параметр id")
            )
            val characters = dbQuery {
                findById(id)?.getCharacters()
            }
            if (characters == null) return ResultResponse.Error(generateMapError(call, 302 to "Не найден пользователь с id $id"))
            ResultResponse.Success(characters)
        } catch (e: Exception) {
            ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
        }
    }

    suspend fun getCharactersById(call: ApplicationCall): ResultResponse {
        return try {
            val id: String = call.parameters["id"] ?: return ResultResponse.Error(
                generateMapError(call, 301 to "Не указан параметр id")
            )
            val charId: String = call.parameters["charId"] ?: return ResultResponse.Error(
                generateMapError(call, 302 to "Не указан параметр charId")
            )
            val character = dbQuery {
                findById(id.toLong())?.getCharacters()?.first { char -> char.id.value == charId.toLong() }?.toSnapshot()
            }
            if (character == null) {
                return ResultResponse.Error(generateMapError(call, 303 to "Не найден пользователь с id $id или персонаж с id $charId"))
            }
            ResultResponse.Success(character)
        } catch (e: Exception) {
            ResultResponse.Error(generateMapError(call, 440 to e.localizedMessage.substringBefore("\n")))
        }
    }

    suspend fun testUserUpdate(call: ApplicationCall): ResultResponse {
        return try {
            val id: String = call.parameters["id"] ?: return ResultResponse.Error(
                generateMapError(call, 301 to "Не указан параметр id")
            )
            val user = dbQuery {
                val tempUser = findById(id.toLong())!!
                update(tempUser) {
                    this.name = "changed name ${System.currentTimeMillis()}"
                }
                tempUser
            }
            ResultResponse.Success(user.toSnapshot())
        } catch (e: Exception) {
            e.printStackTrace()
            ResultResponse.Error(generateMapError(call, 440 to (e.message?:"")))
        }
    }
}