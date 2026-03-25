package application.data.users

import application.data.ExposedBaseDao
import org.jetbrains.exposed.v1.core.statements.UpdateStatement

class DAOusers : ExposedBaseDao<UsersTable, UserEntity>(
    UsersTable,
    UserEntity
) {
    override fun applyEntityToStatement(entity: UserEntity, stmt: UpdateStatement) {
        stmt[table.name] = entity.name
        stmt[table.email] = entity.email
    }
}