package features.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumRarity
import base.exception.BadRequestException
import base.route.BaseRoute
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
            call.respondEntityList(equipService.findByCharacter(charId))
        }

        /** Только надетая экипировка */
        get("/character/{characterId}/equipped") {
            val charId = call.longParam("characterId")
            call.respondEntityList(equipService.findEquipped(charId))
        }

        /** Только в сумке (не надето) */
        get("/character/{characterId}/bag") {
            val charId = call.longParam("characterId")
            call.respondEntityList(equipService.findInBag(charId))
        }

        /** Надеть предмет */
        post("/character/{characterId}/equip/{equipmentId}") {
            val charId = call.longParam("characterId")
            val equipId = call.longParam("equipmentId")
            val (equipped, _) = equipService.equip(charId, equipId)
            call.respondEntity(equipped)
        }

        /** Снять предмет */
        post("/character/{characterId}/unequip/{equipmentId}") {
            val charId = call.longParam("characterId")
            val equipId = call.longParam("equipmentId")
            call.respondEntity(equipService.unequip(charId, equipId))
        }

        /**
         * Сгенерировать предмет (PoE-стиль) и добавить персонажу.
         *
         * POST /api/equipment/generate
         * Body:
         * {
         *   "characterId": 1,
         *   "slot": "HELMET",
         *   "itemLevel": 20,
         *   "rarity": "RARE"
         * }
         */
        post("/generate") {
            val body = call.receiveText()
            val json = Json.parseToJsonElement(body).jsonObject

            val characterId = json["characterId"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: throw BadRequestException("Missing field: characterId")
            val slotStr = json["slot"]?.jsonPrimitive?.content
                ?: throw BadRequestException("Missing field: slot")
            val slot = runCatching { EnumEquipmentType.valueOf(slotStr) }.getOrNull()
                ?: throw BadRequestException("Unknown slot: $slotStr. Valid: ${EnumEquipmentType.entries.filter { it != EnumEquipmentType.UNDEFINED }}")
            val itemLevel = json["itemLevel"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            val rarityStr = json["rarity"]?.jsonPrimitive?.content ?: "COMMON"
            val rarity = runCatching { EnumRarity.valueOf(rarityStr) }.getOrNull()
                ?: throw BadRequestException("Unknown rarity: $rarityStr. Valid: ${EnumRarity.entries}")

            val generated = equipService.generateAndSave(characterId, slot, itemLevel, rarity)
            call.respondEntity(generated)
        }
    }
}
