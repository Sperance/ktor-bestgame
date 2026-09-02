package ru.descend

import application.enums.EnumRarity
import application.koin.allModules
import base.exception.BaseRepositoryExceptions
import base.exception.BaseRouteExceptions
import com.mongodb.MongoBulkWriteException
import kotlinx.coroutines.runBlocking
import com.mongodb.DuplicateKeyException
import config.MongoFactory.transactionExecute
import extensions.printLog
import base.exception.model.CharacterExceptions
import features.data.character.CharacterRepository
import base.exception.model.EquipmentExceptions

import base.exception.model.ItemsExceptions
import base.exception.model.PropertyExceptions
import features.data.user.User
import base.exception.model.UserExceptions
import features.data.equipment.EquipmentRepository
import features.data.equipment.equipment_data.Accessory
import features.data.equipment.equipment_data.Armor
import features.data.equipment.equipment_data.Weapon
import features.data.equipmentName.EquipmentNameRepository
import features.data.user.UserRepository
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.koin.core.context.GlobalContext.startKoin
import org.koin.mp.KoinPlatform.stopKoin
import org.koin.test.inject
import org.koin.test.KoinTest
import kotlin.getValue
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMembers

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MongoTest: KoinTest {

    private val userRepo: UserRepository by inject()
    private val charRepo: CharacterRepository by inject()
    private val equipmentNameRepo: EquipmentNameRepository by inject()
    private val equipmentRepository: EquipmentRepository by inject()
    private val equipmentNameCache: EquipmentNameCache by inject()

    @Before
    fun setup() {
        // Останавливаем Koin если уже запущен
        try {
            stopKoin()
        } catch (_: Exception) {
            // Игнорируем
        }

        // Запускаем Koin с модулями
        startKoin {
            modules(allModules)  // Ваши модули
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun b_insert_stock_data() = runBlocking {
        transactionExecute { session ->
            repeat(100) { ind ->
                userRepo.insert(
                    User(
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
            userRepo.insert(User(
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
            userRepo.insert(User(email = email, name = "newName$curtime", age = 44), session)
        }

        val exception = assertThrows(DuplicateKeyException::class.java) {
            runBlocking {
                transactionExecute { session ->
                    userRepo.insert(User(email = email, name = "newName$curtime", age = 55), session)
                }
            }
        }

        assert(exception.message?.contains("Нарушение уникальности") == true)
    }

    @Test
    fun test_insert_many() = runBlocking {
        val curtime = System.currentTimeMillis()
        val needAddedCount = 3

        val arrayUsers = arrayListOf<User>()
        repeat(needAddedCount) { ind ->
            arrayUsers.add(User(email = "${ind}email@${curtime}.com", name = "user_name_$ind", age = 26))
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

        val arrayUsers = arrayListOf<User>()
        repeat(needAddedCount) { ind ->
            arrayUsers.add(User(email = "ail@${curtime}.com", name = "user_name_", age = 26))
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
                    userRepo.insert(User(email = "em4@eme.ru", name = "error4 name", age = 4), session)
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
                val arrayItems = ArrayList<User>()
                arrayItems.add(User(email = "ara1@eme.ru", name = "error ara name1", age = 25))
                arrayItems.add(User(email = "ara2@eme.ru", name = "error ara name2", age = 43))
                arrayItems.add(User(email = "ara3@eme.ru", name = "error ara name3", age = 3))
                arrayItems.add(User(email = "ara4@eme.ru", name = "error ara name4", age = 86))
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
        val finded = userRepo.findByField(User::name, "TestPlayer")
        printLog("FINDED: $finded")
    }

    @Test
    fun test_exception_files() = run {
        val arrayClasses = listOf(
            BaseRepositoryExceptions::class,
            BaseRouteExceptions::class,
            CharacterExceptions::class,
            EquipmentExceptions::class,
            ItemsExceptions::class,
            PropertyExceptions::class,
            UserExceptions::class
        )

        arrayClasses.forEach { cls ->
            val instance = cls.objectInstance ?: run {
                try {
                    cls.constructors.firstOrNull { it.parameters.isEmpty() }?.call()
                } catch (e: Exception) {
                    null
                }
            }

            if (instance == null) {
                println("⚠️[${cls.simpleName}] Не удалось получить экземпляр")
                return@forEach
            }

            cls.declaredMembers
                .filterIsInstance<KFunction<*>>()
                .filter { it.name.startsWith("funException") }
                .forEach { func ->
                    try {
                        val args = mutableMapOf<KParameter, Any?>()

                        func.parameters.forEachIndexed { index, param ->
                            // Пропускаем receiver (индекс 0 если это метод класса)
                            if (index == 0 && param.type.classifier == cls) {
                                args[param] = instance
                                return@forEachIndexed
                            }

                            // Проверяем, есть ли значение по умолчанию для этого параметра
                            val defaultValue = "<NULL>"

                            // Используем значение по умолчанию
                            args[param] = defaultValue
                        }

                        val result = func.callBy(args)
                        println("✅[${cls.simpleName}] ${func.name}: $result")

                    } catch (e: Exception) {
                        println("❌[${cls.simpleName}] ${func.name}: ${e.message}")
                    }
                }
        }
    }

    @Test
    fun test_get_equip(): Unit = runBlocking {
        val items = equipmentRepository.findAll()
        items.forEach {
            printLog(it)

            when(it) {
                is Weapon -> {
                    it.params.clear()
                    it.damage = 333
                }
                is Armor -> {
                    it.defense = 444
                }
                is Accessory -> {
                    // Accessory handling
                }
            }
        }

        items.forEach {
            printLog(it)
        }

        transactionExecute { session ->
            equipmentRepository.bulkUpdate(items, session)
        }
    }

    @Test
    fun test_generate_items(): Unit = runBlocking {

        val generator = EquipmentGenerator()
        val allCounter = 100

        val char = charRepo.findAll().first()
        val counter = mutableMapOf<EnumRarity, Int>()
        repeat(allCounter) {
            val item = generator.generateEquipment(char)
            printLog("ITEM: $item")
            counter[item.rarity] = (counter[item.rarity] ?: 0) + 1
        }
        val sorted = counter.toSortedMap()
        sorted.forEach { (rarity, i) ->
            printLog("$rarity: $i (${(i.toDouble() / allCounter.toDouble() * 100).roundTo(5)}%)")
        }
    }
}

fun Double.roundTo(decimals: Int): Double {
    val factor = 10.0.pow(decimals)
    return (this * factor).roundToInt() / factor
}