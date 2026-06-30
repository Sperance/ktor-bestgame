package ru.descend

import com.mongodb.MongoBulkWriteException
import kotlinx.coroutines.runBlocking
import com.mongodb.DuplicateKeyException
import config.MongoFactory.transactionExecute
import extensions.printLog
import features.userMongo.UserMongo
import features.userMongo.UserRepositoryMongo
import org.bson.types.ObjectId
import org.junit.Assert.assertThrows
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

data class Counter(
    val name: String,
    val value: Int,
    val inner: InnerCounter,
)

data class InnerCounter(
    val count: Int,
    val message: String,
)

open class StockStatMongo(
    val name: String,
    val value: Double
)

data class BaseStatMongo(
    val description: String
): StockStatMongo("", 1.0)

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MongoTest {

    private val userRepo = UserRepositoryMongo

    @Test
    fun b_insert_stock_data() = runBlocking {
        transactionExecute { session ->
            repeat(100) { ind ->
                userRepo.insert(
                    UserMongo(
                        email = "email$ind@domain.com",
                        name = "user_$ind",
                        age = (20..60).random()
                    ), session)
            }
        }
        assert(userRepo.count() == 100L)
    }

    @Test
    fun test_insert_simple() = runBlocking {
        val curtime = System.currentTimeMillis()

        val res = transactionExecute { session ->
            userRepo.insert(UserMongo(
                email = "newEmail@$curtime",
                name = "newName1",
                age = (15..40).random()
            ), session)
        }
    }

    @Test
    fun test_insert_duplicate_email() = runBlocking {
        val curtime = System.currentTimeMillis()
        val email = "newEmail@$curtime"

        transactionExecute { session ->
            userRepo.insert(UserMongo(email = email, name = "newName$curtime", age = 44), session)
        }

        val exception = assertThrows(DuplicateKeyException::class.java) {
            runBlocking {
                transactionExecute { session ->
                    userRepo.insert(UserMongo(email = email, name = "newName$curtime", age = 55), session)
                }
            }
        }

        assert(exception.message?.contains("Нарушение уникальности") == true)
    }

    @Test
    fun test_insert_many() = runBlocking {
        val curtime = System.currentTimeMillis()
        val needAddedCount = 3

        val arrayUsers = arrayListOf<UserMongo>()
        repeat(needAddedCount) { ind ->
            arrayUsers.add(UserMongo(email = "${ind}email@${curtime}.com", name = "user_name_$ind", age = 26))
        }

        val sizeBefore = userRepo.count()
        val res = transactionExecute { session ->
            userRepo.insertMany(arrayUsers, session)
        }
        val sizeAfter = userRepo.count()

        assert(sizeBefore == (sizeAfter - needAddedCount))
    }

    @Test
    fun test_insert_many_duplicate(): Unit = runBlocking {
        val curtime = System.currentTimeMillis()
        val needAddedCount = 3

        val arrayUsers = arrayListOf<UserMongo>()
        repeat(needAddedCount) { ind ->
            arrayUsers.add(UserMongo(email = "ail@${curtime}.com", name = "user_name_", age = 26))
        }

        val exception = assertThrows(MongoBulkWriteException::class.java) {
            runBlocking {
                transactionExecute { session ->
                    userRepo.insertMany(arrayUsers, session)
                }
            }
        }
        assert(exception.writeErrors.first().code == 11000)
    }

    @Test
    fun test_find_one() : Unit = runBlocking {
        val firstId = userRepo.findAll().first()
        val finded = userRepo.findById(firstId._id)
        assert(finded != null)
        assert(finded!!._id == firstId._id)
        assert(finded == firstId)
    }

    @Test
    fun test_drop_validate_age() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                transactionExecute { session ->
                    userRepo.insert(UserMongo(email = "em4@eme.ru", name = "error4 name", age = 4), session)
                }
            }
        }
        assert(exception.message.equals("'error4 name' has invalid age: 4")) {
            "Need message: \"'error4 name' has invalid age: 4\"\nExpected message: \"${exception.message}\""
        }
    }

    @Test
    fun test_drop_validate_age_many() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                val arrayItems = ArrayList<UserMongo>()
                arrayItems.add(UserMongo(email = "ara1@eme.ru", name = "error ara name1", age = 25))
                arrayItems.add(UserMongo(email = "ara2@eme.ru", name = "error ara name2", age = 43))
                arrayItems.add(UserMongo(email = "ara3@eme.ru", name = "error ara name3", age = 3))
                arrayItems.add(UserMongo(email = "ara4@eme.ru", name = "error ara name4", age = 86))
                transactionExecute { session ->
                    userRepo.insertMany(arrayItems, session)
                }
            }
        }
        assert(exception.message.equals("'error ara name3' has invalid age: 3")) {
            "Need message: \"'error ara name3' has invalid age: 3\"\nExpected message: \"${exception.message}\""
        }
    }

//    @Test
//    fun test_watch_items(): Unit = runBlocking {
//        launch {
//            userRepo.watchAll().collect {
//                println("[${it.operationType}]: ${it.documentKey}")
//            }
//        }
//        launch {
//            transactionExecute { session ->
//                repeat(5) { counter ->
//                    delay(1000)
//                    userRepo.insert(UserMongo(email = "_watch$counter@email.com", name = "name watch", age = 52), session)
//                }
//            }
//        }.join()
//        launch {
//            userRepo.findByFilterFlow {
//                UserMongo::name eq "name watch"
//            }.collect { user ->
//                launch {
//                    delay(500)
//                    userRepo.deleteById(user._id)
//                }
//            }
//        }
//    }

    @Test
    fun test_bulk_update(): Unit = runBlocking {
        transactionExecute { session ->

            val lastUsers = userRepo.findAll().takeLast(3)
            lastUsers.forEach {
                it.name += "asd"
            }

            userRepo.bulkUpdate(lastUsers, session)
        }

        val count = userRepo.findAll().takeLast(3).count { it.name.contains("asd") }
        assert(count == 3)
    }

    @Test
    fun test_update_entity(): Unit = runBlocking {
        transactionExecute { session ->
            val firstUser = userRepo.findAll().first()
            firstUser.name = "CHANGED_TEST"
            userRepo.update(firstUser, session)
        }

        val firstUser = userRepo.findAll().first()
        assert(firstUser.name == "CHANGED_TEST")
    }

    @Test
    fun test_update_field(): Unit = runBlocking {
        transactionExecute { session ->
            val firstUser = userRepo.findAll().first()
            userRepo.updateFields(firstUser, mapOf("name" to "UPDATED_TEST_NAME"), session)
        }

        val firstUser = userRepo.findAll().first()
        assert(firstUser.name == "UPDATED_TEST_NAME")
    }

    @Test
    fun test_delete_simple(): Unit = runBlocking {
        val res = transactionExecute { session ->
            val firstUser = userRepo.findAll().first()
            userRepo.deleteById(firstUser, session)
        }
        assert(res.wasAcknowledged())
        assert(res.deletedCount == 1L)
    }

    @Test
    fun test_delete_soft(): Unit = runBlocking {
        val res = transactionExecute { session ->
            val firstUser = userRepo.findAll().first()
            userRepo.softDelete(firstUser, session)
        }
        assert(res.wasAcknowledged())
        assert(res.modifiedCount == 1L)
    }

    @Test
    fun test_find_field(): Unit = runBlocking {
        val finded = UserRepositoryMongo.findByField(UserMongo::name, "TestPlayer")
        printLog("FINDED: $finded")
    }
}