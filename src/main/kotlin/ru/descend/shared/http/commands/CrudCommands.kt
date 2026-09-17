package ru.descend.shared.http.commands

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable data class UpdateCommand(val expectedVersion: Long, val changes: JsonObject)
@Serializable data class DeleteCommand(val expectedVersion: Long)
@Serializable data class CreateUserCommand(val name: String, val email: String, val login: String, val password: String, val age: Int)
@Serializable data class CreateCharacterCommand(val name: String, val description: String = "")
@Serializable data class ChangePasswordCommand(val expectedVersion: Long, val currentPassword: String, val newPassword: String)
@Serializable data class ChangeRoleCommand(val expectedVersion: Long, val role: ru.descend.domain.enums.EnumUserRoles)
