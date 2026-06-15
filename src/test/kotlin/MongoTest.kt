package ru.descend

import com.mongodb.MongoBulkWriteException
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.runBlocking
import mongo_test.DuplicateKeyException
import mongo_test.UserMongo
import mongo_test.UserRepositoryMongo
import org.junit.Assert.assertThrows
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.test.DefaultAsserter.assertNotNull
import kotlin.test.DefaultAsserter.assertTrue

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

    private val uri = "mongodb://localhost:27017/my_first_project"
    private val client = MongoClient.create(uri)
    private val db = client.getDatabase("my_first_project")
    private val userRepo = UserRepositoryMongo(db)

    @Test
    fun a_clear_repository() = runBlocking {
        userRepo.deleteAll()
        assert(userRepo.count() == 0L)
    }

    @Test
    fun b_insert_stock_data() = runBlocking {
        repeat(100) { ind ->
            userRepo.insert(UserMongo(email = "email$ind@domain.com", name="user_$ind", age = (10..60).random()))
        }
    }

    @Test
    fun test_insert_simple() = runBlocking {
        val curtime = System.currentTimeMillis()
        val res = userRepo.insert(UserMongo(
            email = "newEmail@$curtime",
            name = "newName1",
            age = (15..40).random()
        ))

        assertTrue("Insert should be acknowledged", res.wasAcknowledged())
        assertNotNull("Inserted ID should not be null", res.insertedId)
    }

    @Test
    fun test_insert_duplicate_email() = runBlocking {
        val curtime = System.currentTimeMillis()
        val email = "newEmail@$curtime"

        userRepo.insert(UserMongo(email = email, name = "newName$curtime", age = 44))

        val exception = assertThrows(DuplicateKeyException::class.java) {
            runBlocking {
                userRepo.insert(UserMongo(email = email, name = "newName$curtime", age = 55))
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
        val res = userRepo.insertMany(arrayUsers)
        val sizeAfter = userRepo.count()

        assert(sizeBefore == (sizeAfter - needAddedCount))
        assert(res.wasAcknowledged())
        assert(res.insertedIds.size == needAddedCount)
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
                userRepo.insertMany(arrayUsers)
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
    fun test_find_one_concern(): Unit = runBlocking {
        val firstId = userRepo.findAll().first()

        //TODO Эмулировать несколько узлов
        val finded = userRepo.findByIdForUpdate(firstId._id)

        assert(finded != null)
        assert(finded!!._id == firstId._id)
        assert(finded == firstId)
    }

    @Test
    fun test_find_by_filter_flow(): Unit = runBlocking {
        var findedObject: UserMongo? = null
        userRepo.findByFilterFlow {
            UserMongo::name eq "user_61"
        }.collect {
            findedObject = it
        }

        assert(findedObject != null)
        assert(findedObject!!.name == "user_61")
    }
}