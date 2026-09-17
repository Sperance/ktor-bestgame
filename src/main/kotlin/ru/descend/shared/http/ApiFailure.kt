package ru.descend.shared.http

import io.ktor.http.HttpStatusCode

class ApiFailure(val status: HttpStatusCode, val code: String, message: String) : RuntimeException(message)
fun invalid(message: String): Nothing = throw ApiFailure(HttpStatusCode.BadRequest, "INVALID_REQUEST", message)
fun missing(): Nothing = throw ApiFailure(HttpStatusCode.NotFound, "NOT_FOUND", "Resource not found")
fun forbidden(): Nothing = throw ApiFailure(HttpStatusCode.Forbidden, "FORBIDDEN", "Operation is not permitted")
fun conflict(): Nothing = throw ApiFailure(HttpStatusCode.Conflict, "VERSION_CONFLICT", "Reload the resource and retry with its current version")
fun checkVersion(actual: Long, expected: Long) { if (expected < 0) invalid("Invalid expectedVersion"); if (actual != expected) conflict() }
fun checkedId(value: String?): String = value?.takeIf { it.matches(Regex("[0-9a-fA-F]{24}")) } ?: invalid("Expected a 24-character hexadecimal id")
