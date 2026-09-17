package ru.descend.features.character.http

import io.ktor.server.response.respond
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import ru.descend.features.character.model.Character
import ru.descend.features.character.persistence.CharacterRepository
import ru.descend.features.character.application.*
import ru.descend.infrastructure.security.actor
import ru.descend.shared.http.*

class CharacterRoute(val repo: CharacterRepository) : BaseRoute<Character, Character>(repo, Character.serializer(), Character.serializer(), { it }) {
    override fun additionalRoutes(route: Route) = with(route) {
        val service by inject<EquipmentService>()
        val rewards by inject<InventoryCommandService>()
        // Инвентарь не ограничен по размеру, поэтому читается страницами: size + курсор after.
        get("/inventory/equipments") { call.respond(ApiMongoResponse.ok(service.view(call.queryParam("characterId"), call.actor(), call.pageSize(), call.after()))) }
        get("/inventory/equipped") { call.respond(ApiMongoResponse.ok(service.equipped(call.queryParam("characterId"), call.actor()))) }
        get("/{id}/stats") { call.respond(ApiMongoResponse.ok(service.stats(service.character(checkedId(call.parameters["id"]), call.actor())))) }
        get("/{id}/equipment") { call.respond(ApiMongoResponse.ok(service.view(checkedId(call.parameters["id"]), call.actor(), call.pageSize(), call.after()))) }
        post("/{id}/compareEquipment") { call.respond(ApiMongoResponse.ok(service.compare(checkedId(call.parameters["id"]), call.actor(), call.receiveCommand()))) }
        // Предметы без стаков: страница принадлежащих единиц и отдельно вычисляемые количества.
        get("/{id}/items") { call.respond(ApiMongoResponse.ok(service.items(checkedId(call.parameters["id"]), call.actor(), call.itemPageSize(), call.after()))) }
        get("/{id}/itemTotals") { call.respond(ApiMongoResponse.ok(service.itemTotals(checkedId(call.parameters["id"]), call.actor()))) }
        get("/{id}/craftOptions") { call.respond(ApiMongoResponse.ok(service.craftOptions(checkedId(call.parameters["id"]), call.actor(), call.queryParam("equipmentUuid")))) }
        post("/{id}/equip") { call.respond(ApiMongoResponse.ok(service.equip(checkedId(call.parameters["id"]), call.actor(), call.receiveCommand()))) }
        post("/{id}/unequip") { call.respond(ApiMongoResponse.ok(service.unequip(checkedId(call.parameters["id"]), call.actor(), call.receiveCommand()))) }
        post("/inventory/itemToInventory") { call.respond(ApiMongoResponse.ok(service.grant(call.queryParam("characterId"), call.actor(), call.receiveCommand()))) }
        post("/inventory/addItem") { call.respond(ApiMongoResponse.ok(rewards.adjust(call.queryParam("characterId"), call.actor(), call.receiveCommand()))) }
        post("/{id}/redeem") { call.respond(ApiMongoResponse.ok(rewards.redeem(checkedId(call.parameters["id"]), call.actor(), call.receiveCommand()))) }
        post("/{id}/useRecipe") { call.respond(ApiMongoResponse.ok(rewards.recipe(checkedId(call.parameters["id"]), call.actor(), call.receiveCommand()))) }
    }

    private fun io.ktor.server.application.ApplicationCall.pageSize(): Int {
        val size = request.queryParameters["size"]?.toIntOrNull() ?: ru.descend.features.character.persistence.CharacterEquipmentRepository.DEFAULT_PAGE_SIZE
        if (size !in 1..ru.descend.features.character.persistence.CharacterEquipmentRepository.MAX_PAGE_SIZE) invalid("Invalid inventory page size")
        return size
    }

    private fun io.ktor.server.application.ApplicationCall.itemPageSize(): Int {
        val size = request.queryParameters["size"]?.toIntOrNull() ?: ru.descend.features.character.persistence.CharacterInventoryRepository.DEFAULT_PAGE_SIZE
        if (size !in 1..ru.descend.features.character.persistence.CharacterInventoryRepository.MAX_PAGE_SIZE) invalid("Invalid inventory page size")
        return size
    }

    private fun io.ktor.server.application.ApplicationCall.after(): String? = request.queryParameters["after"]?.let(::checkedId)
}
