package features.equipment

import base.route.BaseRoute
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.http.HttpStatusCode
import base.model.ApiResponse
import base.model.apiResponseUnitSerializer

class EquipmentRoute(
    private val equipService: EquipmentService = EquipmentService()
) : BaseRoute<Equipment, EquipmentTable>(
    service = equipService,
    basePath = "/api/equipment",
    entitySerializer = Equipment.serializer()
) {

    override fun additionalRoutes(route: Route) = with(route) {

        /** Вся экипировка персонажа (и надетая, и в сумке) */
        get("/character/{characterId}") {
            val charId = call.longParam("characterId")
            val items = equipService.findByCharacter(charId)
            call.respondEntityList(items)
        }

        /** Только надетая экипировка */
        get("/character/{characterId}/equipped") {
            val charId = call.longParam("characterId")
            val items = equipService.findEquipped(charId)
            call.respondEntityList(items)
        }

        /** Только в сумке (не надето) */
        get("/character/{characterId}/bag") {
            val charId = call.longParam("characterId")
            val items = equipService.findInBag(charId)
            call.respondEntityList(items)
        }

        /** Надеть предмет */
        post("/character/{characterId}/equip/{equipmentId}") {
            val charId = call.longParam("characterId")
            val equipId = call.longParam("equipmentId")
            val (equipped, unequipped) = equipService.equip(charId, equipId)
            call.respondEntity(equipped)
        }

        /** Снять предмет */
        post("/character/{characterId}/unequip/{equipmentId}") {
            val charId = call.longParam("characterId")
            val equipId = call.longParam("equipmentId")
            val item = equipService.unequip(charId, equipId)
            call.respondEntity(item)
        }
    }
}
