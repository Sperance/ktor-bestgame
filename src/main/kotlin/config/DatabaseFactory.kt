package config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import features.characters.CharacterTable
import features.user.UsersTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Фабрика для инициализации и настройки подключения к базе данных.
 *
 * **Основная ответственность:** Централизованная настройка всех аспектов
 * соединения с БД: пул соединений (HikariCP), подключение Exposed, создание схемы.
 *
 * **Ключевые особенности:**
 * - Использует высокопроизводительный пул соединений HikariCP
 * - Настраивает уровень изоляции транзакций (REPEATABLE READ)
 * - Автоматически создаёт/пересоздаёт таблицы при инициализации
 * - Поддерживает PostgreSQL (легко расширяется для других БД)
 * - Параметры подключения имеют значения по умолчанию для разработки
 *
 * **Уровни изоляции транзакций:**
 * - `READ_UNCOMMITTED` — минимальная изоляция (грязное чтение)
 * - `READ_COMMITTED` — стандартный уровень PostgreSQL
 * - `REPEATABLE_READ` — предотвращает неповторяющееся чтение (используется здесь)
 * - `SERIALIZABLE` — максимальная изоляция (медленнее)
 *
 * **Важно:** В production-окружении параметры подключения должны
 * передаваться через переменные окружения или конфигурационные файлы,
 * а не быть захардкоженными!
 *
 * @see <a href="https://github.com/brettwooldridge/HikariCP">HikariCP Documentation</a>
 * @see <a href="https://github.com/JetBrains/Exposed">Exposed ORM</a>
 *
 * @sample
 * ```kotlin
 * // Базовое использование в main-функции
 * fun main() {
 *     DatabaseFactory.init()
 *     println("Database initialized successfully")
 * }
 *
 * // Использование с кастомными параметрами
 * fun initProduction() {
 *     DatabaseFactory.init(
 *         url = System.getenv("DB_URL"),
 *         user = System.getenv("DB_USER"),
 *         password = System.getenv("DB_PASSWORD"),
 *         tables = arrayOf(UsersTable, PostsTable, CommentsTable)
 *     )
 * }
 *
 * // Использование в тестах (создание чистой БД)
 * @BeforeEach
 * fun setup() {
 *     DatabaseFactory.init(
 *         url = "jdbc:postgresql://localhost:5432/test_db",
 *         user = "test",
 *         password = "test",
 *         tables = arrayOf(UsersTable, PostsTable)
 *     )
 * }
 * ```
 */
object DatabaseFactory {

    /**
     * Инициализирует подключение к базе данных и создаёт схему.
     *
     * **Процесс инициализации:**
     * 1. Создаёт пул соединений HikariCP с указанными параметрами
     * 2. Подключает Exposed к этому пулу
     * 3. В транзакции удаляет все существующие таблицы (⚠️ ОПАСНО!)
     * 4. Создаёт таблицы заново
     *
     * **⚠️ ПРЕДУПРЕЖДЕНИЕ О БЕЗОПАСНОСТИ:**
     * Текущая реализация вызывает `SchemaUtils.drop(*tables)`, что УНИЧТОЖАЕТ
     * все данные в указанных таблицах! Это подходит только для:
     * - Разработки и локального тестирования
     * - Демонстрационных приложений
     * - Интеграционных тестов с чистой базой
     *
     * **Для production используйте миграции (например, Flyway или Liquibase)!**
     *
     * @param url JDBC URL для подключения к базе данных
     *            Формат: `jdbc:postgresql://host:port/database?параметры`
     *            По умолчанию: удалённый PostgreSQL на beget
     * @param user Имя пользователя базы данных
     * @param password Пароль пользователя базы данных
     * @param tables Массив таблиц для создания (порядок важен из-за внешних ключей!)
     *
     * @throws Exception При ошибках подключения или создания таблиц
     *
     * @sample
     * ```kotlin
     * // Production-конфигурация с переменными окружения
     * DatabaseFactory.init(
     *     url = System.getenv("DATABASE_URL") ?: error("DATABASE_URL not set"),
     *     user = System.getenv("DATABASE_USER") ?: error("DATABASE_USER not set"),
     *     password = System.getenv("DATABASE_PASSWORD") ?: error("DATABASE_PASSWORD not set"),
     *     tables = arrayOf(UsersTable, PostsTable, CommentsTable)
     * )
     *
     * // Для локальной разработки
     * DatabaseFactory.init(
     *     url = "jdbc:postgresql://localhost:5432/myapp_dev",
     *     user = "dev_user",
     *     password = "dev_password"
     * )
     * ```
     */
    fun init(
        url: String = "jdbc:postgresql://jouquemuprosa.beget.app:5432/descend_db?targetServerType=master&ssl=false&sslmode=disable",
        user: String = "descend",
        password: String = "Elbrinom666.",
        tables: Array<Table>
    ) {
        val dataSource = hikari(url, user, password)
        Database.connect(dataSource)

        // Создаём таблицы если не существуют
        transaction {
            SchemaUtils.drop(*tables)
            SchemaUtils.create(*tables)
        }
    }

    /**
     * Создаёт и настраивает пул соединений HikariCP.
     *
     * **HikariCP** — это высокопроизводительный пул соединений для Java/Kotlin.
     * Он управляет пулом открытых соединений с БД, что значительно быстрее,
     * чем открывать новое соединение на каждый запрос.
     *
     * **Настройки пула:**
     * - `maximumPoolSize = 10` — максимальное количество соединений в пуле
     * - `isAutoCommit = false` — отключаем автокоммит (управляем транзакциями вручную)
     * - `transactionIsolation = "TRANSACTION_REPEATABLE_READ"` — уровень изоляции
     *
     * **Почему REPEATABLE READ:**
     * - Предотвращает неповторяющееся чтение (два одинаковых SELECT могут вернуть разные данные)
     * - Хороший баланс между изоляцией и производительностью
     * - Поддерживает оптимистичную блокировку (нашу `version`)
     *
     * **Почему autoCommit = false:**
     * - Exposed сам управляет транзакциями через `transaction { }` блоки
     * - Позволяет группировать несколько операций в одну транзакцию
     * - Обеспечивает атомарность операций
     *
     * @param url JDBC URL для подключения
     * @param user Имя пользователя
     * @param password Пароль
     * @return Настроенный источник данных HikariCP
     *
     * @sample
     * ```kotlin
     * // Кастомная настройка для production
     * private fun hikariProduction(url: String, user: String, password: String): HikariDataSource {
     *     val config = HikariConfig().apply {
     *         jdbcUrl = url
     *         username = user
     *         this.password = password
     *         maximumPoolSize = 20  // Больше соединений для продакшна
     *         minimumIdle = 5       // Поддерживаем минимум idle-соединений
     *         connectionTimeout = 30000  // 30 секунд таймаут
     *         idleTimeout = 600000       // 10 минут
     *         maxLifetime = 1800000      // 30 минут
     *     }
     *     return HikariDataSource(config)
     * }
     * ```
     */
    private fun hikari(url: String, user: String, password: String): HikariDataSource {
        val config = HikariConfig().apply {
            jdbcUrl = url
            driverClassName = "org.postgresql.Driver"
            username = user
            this.password = password
            maximumPoolSize = 10
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(config)
    }
}