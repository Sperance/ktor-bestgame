package config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object DatabaseFactory {

    fun init(
        url: String = "jdbc:postgresql://jouquemuprosa.beget.app:5432/descend_db?targetServerType=master&ssl=false&sslmode=disable",
        user: String = "descend",
        password: String = "Elbrinom666.",
        tables: Array<Table> = emptyArray()
    ) {
        val dataSource = hikari(url, user, password)
        Database.connect(dataSource)

        // Создаём таблицы если не существуют
        transaction {
            SchemaUtils.drop(*tables)
            SchemaUtils.create(*tables)
        }
    }

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