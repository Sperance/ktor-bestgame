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
        get("/inventory/equipments") { call.respond(ApiMongoResponse.ok(service.view(call.queryParam("characterId"), call.actor()))) }
        get("/inventory/equipped") { val view = service.view(call.queryParam("characterId"), call.actor()); call.respond(ApiMongoResponse.ok(view.inventory.filter { it.uuid in view.equipped.values })) }
        get("/{id}/stats") { call.respond(ApiMongoResponse.ok(service.view(checkedId(call.parameters["id"]), call.actor()).stats)) }
        get("/{id}/equipment") { call.respond(ApiMongoResponse.ok(service.view(checkedId(call.parameters["id"]), call.actor()))) }
        post("/{id}/compareEquipment") { call.respond(ApiMongoResponse.ok(service.compare(checkedId(call.parameters["id"]), call.actor(), call.receiveCommand()))) }
        get("/{id}/craftOptions") { call.respond(ApiMongoResponse.ok(service.craftOptions(checkedId(call.parameters["id"]), call.actor(), call.queryParam("equipmentUuid")))) }
        post("/{id}/equip") { call.respond(ApiMongoResponse.ok(service.equip(checkedId(call.parameters["id"]), call.actor(), call.receiveCommand()))) }
        post("/{id}/unequip") { call.respond(ApiMongoResponse.ok(service.unequip(checkedId(call.parameters["id"]), call.actor(), call.receiveCommand()))) }
        post("/inventory/itemToInventory") { call.respond(ApiMongoResponse.ok(service.grant(call.queryParam("characterId"), call.actor(), call.receiveCommand()))) }
        post("/inventory/addItem") { call.respond(ApiMongoResponse.ok(rewards.adjust(call.queryParam("characterId"), call.actor(), call.receiveCommand()))) }
        post("/{id}/redeem") { call.respond(ApiMongoResponse.ok(rewards.redeem(checkedId(call.parameters["id"]), call.actor(), call.receiveCommand()))) }
        post("/{id}/useRecipe") { call.respond(ApiMongoResponse.ok(rewards.recipe(checkedId(call.parameters["id"]), call.actor(), call.receiveCommand()))) }
    }
}
