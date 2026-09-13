package ru.descend.features.poe.http

import ru.descend.features.poe.application.PoeService

import ru.descend.features.poe.catalog.PoeCatalog
import ru.descend.features.poe.catalog.PoeRecord

import ru.descend.features.poe.catalog.string
import ru.descend.features.poe.domain.PoeCapabilities
import ru.descend.features.poe.domain.PoeCrafting
import ru.descend.features.poe.domain.PoeCurrency
import ru.descend.features.poe.domain.PoeInventory

import ru.descend.features.poe.persistence.MongoModifierCatalog

import ru.descend.domain.enums.EnumUserRoles
import ru.descend.shared.error.BaseException
import ru.descend.shared.http.ApiMongoResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.equipment.persistence.EquipmentRepository
import ru.descend.features.user.persistence.UserRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import java.security.SecureRandom
import java.util.Base64
import java.util.Date

@Serializable
@kotlinx.serialization.SerialName("features.poe.CurrencyOption")
data class CurrencyOption(val id: PoeCurrency, val name: String, val itemId: String)
