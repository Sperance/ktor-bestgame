package ru.descend.bootstrap.seed

import ru.descend.domain.enums.EnumUserRoles
import ru.descend.features.character.model.Character
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.user.model.User
import ru.descend.features.user.persistence.UserRepository
import ru.descend.infrastructure.mongo.MongoFactory.transactionExecute

/** Explicit development fixture; never overwrites an existing account or password. */
class DemoSeeder(private val users: UserRepository, private val characters: CharacterRepository) {
    suspend fun seed(password: String) {
        require(password.length >= 12) { "Demo password must have at least 12 characters" }
        transactionExecute("seed.demo") { session ->
            if (users.count(session) == 0L) {
                val user = users.insert(User(name = "Administrator", email = "admin@example.invalid",
                    age = 25, login = "admin", password = password, role = EnumUserRoles.ADMIN), session)
                characters.insert(Character(userId = user._id, name = "Exile", description = "Development character"), session)
            }
        }
    }
}
