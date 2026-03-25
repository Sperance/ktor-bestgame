package application

import application.data.characters.CharactersTable
import application.data.equipments.EquipmentsTable
import application.data.inventory.InventoryTable
import application.data.users.UsersTable
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseConfig {
    private var dataSource: HikariDataSource? = null
    private lateinit var database: Database

    fun init(
        url: String = "jdbc:postgresql://jouquemuprosa.beget.app:5432/descend_db?targetServerType=master&ssl=false&sslmode=disable",
        driver: String = "org.postgresql.Driver",
        userSet: String = "descend",
        passwordSet: String = "Elbrinom666.",
        maxPoolSize: Int = 10,
        connectionTimeoutSet: Long = 30000,
        idleTimeoutSet: Long = 600000,
        maxLifetimeSet: Long = 1800000
    ) {
        val config = HikariConfig().apply {
            jdbcUrl = url
            driverClassName = driver
            username = userSet
            password = passwordSet
            maximumPoolSize = maxPoolSize
            connectionTimeout = connectionTimeoutSet
            idleTimeout = idleTimeoutSet
            maxLifetime = maxLifetimeSet
            validationTimeout = 5000
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"

            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            addDataSourceProperty("reWriteBatchedInserts", "true")
            addDataSourceProperty("loggerLevel", "ON")
        }

        val configs = DatabaseConfig {
            useNestedTransactions = true
        }

        dataSource = HikariDataSource(config)
        database = Database.connect(dataSource!!, databaseConfig = configs)

        transaction {
            SchemaUtils.drop(EquipmentsTable, CharactersTable, UsersTable, InventoryTable)
            SchemaUtils.create(InventoryTable, UsersTable, CharactersTable, EquipmentsTable)

//            EquipmentsTable.deleteAll()
//            CharactersTable.deleteAll()
//            UsersTable.deleteAll()
        }
    }

    fun close() {
        dataSource?.close()
        dataSource = null
    }

    suspend fun <T> dbQuery(block: suspend JdbcTransaction.(JdbcTransaction) -> T): T {
        return suspendTransaction(db = database) { block(this) }
    }
}