package features.modifier

import application.enums.EnumEquipmentType
import base.exception.BadRequestException
import base.exception.NotFoundException
import base.model.ApiResponse
import base.model.apiResponseSerializer
import features.characters.CharacterRepository
import features.equipment.EquipmentRepository
import features.property.PropertyCache
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.application.Application
import kotlinx.serialization.Serializable

fun Application.configureModifierRoutes() {
    val charRepo = CharacterRepository()
    val equipRepo = EquipmentRepository()

    routing {

        /**
         * GET /api/modifier/templates
         * Список всех явных шаблонов аффиксов.
         */
        get("/api/modifier/templates") {
            val items = ModifierPool.explicitTemplates.map { t ->
                TemplateInfo(
                    name = t.name,
                    stat = t.stat.name,
                    statNameRu = t.stat.nameRu,
                    modType = t.modType.name,
                    modTypeRu = t.modType.displayRu,
                    category = t.category.name,
                    group = t.group.name,
                    tags = t.tags,
                    weight = t.weight,
                    tiers = t.tiers.map { tier ->
                        TierInfo(tier.tier, tier.minItemLevel, tier.minValue, tier.maxValue, tier.weight)
                    }
                )
            }
            call.respond(
                HttpStatusCode.OK,
                ApiResponse.ok(items, "Modifier templates")
            )
        }

        /**
         * GET /api/modifier/pool/{slot}
         * Пул аффиксов для конкретного слота при данном itemLevel.
         *
         * Query params: itemLevel (default=1)
         */
        get("/api/modifier/pool/{slot}") {
            val slotStr = call.parameters["slot"]
                ?: throw BadRequestException("Missing path param: slot")
            val slot = runCatching { EnumEquipmentType.valueOf(slotStr.uppercase()) }.getOrNull()
                ?: throw BadRequestException("Unknown slot: $slotStr")
            val itemLevel = call.request.queryParameters["itemLevel"]?.toIntOrNull() ?: 1

            val prefixPool = ModifierPool.getExplicitPool(slot, itemLevel, application.enums.EnumModifierCategory.PREFIX)
            val suffixPool = ModifierPool.getExplicitPool(slot, itemLevel, application.enums.EnumModifierCategory.SUFFIX)
            val implicitPool = ModifierPool.getImplicitPool(slot, itemLevel)

            val result = PoolInfo(
                slot = slot.name,
                itemLevel = itemLevel,
                prefixes = prefixPool.map { (t, tier) ->
                    PoolEntry(t.name, t.stat.nameRu, t.modType.displayRu, tier.tier, tier.minValue, tier.maxValue, tier.weight)
                },
                suffixes = suffixPool.map { (t, tier) ->
                    PoolEntry(t.name, t.stat.nameRu, t.modType.displayRu, tier.tier, tier.minValue, tier.maxValue, tier.weight)
                },
                implicits = implicitPool.map { (t, tier) ->
                    PoolEntry(t.name, t.stat.nameRu, t.modType.displayRu, tier.tier, tier.minValue, tier.maxValue, tier.weight)
                }
            )
            call.respond(HttpStatusCode.OK, ApiResponse.ok(result, "Modifier pool for slot $slot"))
        }

        /**
         * GET /api/modifier/character/{id}/stats
         * Рассчитать итоговые характеристики персонажа с учётом надетых предметов.
         */
        get("/api/modifier/character/{id}/stats") {
            val charId = call.parameters["id"]?.toLongOrNull()
                ?: throw BadRequestException("Invalid character id")

            val character = charRepo.findById(charId)
                ?: throw NotFoundException("Character(id=$charId) not found")
            val equippedItems = equipRepo.findEquipped(charId)

            val statEnumById = PropertyCache.getIdToEnumMap()
            val results = StatCalculationService.calculateCharacterStats(
                characterParams = character.params,
                equippedItems = equippedItems,
                statEnumById = statEnumById
            )

            val statResults = results.map { r ->
                StatResultDto(
                    statId = r.statId,
                    statName = r.statEnum?.nameRu ?: "Unknown",
                    base = r.base,
                    flat = r.flat,
                    increased = r.increased,
                    reduced = r.reduced,
                    more = r.moreList,
                    less = r.lessList,
                    final = r.final
                )
            }
            call.respond(HttpStatusCode.OK, ApiResponse.ok(statResults, "Calculated stats for character $charId"))
        }
    }
}

// ===== DTOs =====

@Serializable
data class TierInfo(
    val tier: Int,
    val minItemLevel: Int,
    val minValue: Double,
    val maxValue: Double,
    val weight: Int
)

@Serializable
data class TemplateInfo(
    val name: String,
    val stat: String,
    val statNameRu: String,
    val modType: String,
    val modTypeRu: String,
    val category: String,
    val group: String,
    val tags: List<String>,
    val weight: Int,
    val tiers: List<TierInfo>
)

@Serializable
data class PoolEntry(
    val name: String,
    val statNameRu: String,
    val modTypeRu: String,
    val tier: Int,
    val minValue: Double,
    val maxValue: Double,
    val weight: Int
)

@Serializable
data class PoolInfo(
    val slot: String,
    val itemLevel: Int,
    val prefixes: List<PoolEntry>,
    val suffixes: List<PoolEntry>,
    val implicits: List<PoolEntry>
)

@Serializable
data class StatResultDto(
    val statId: Long,
    val statName: String,
    val base: Double,
    val flat: Double,
    val increased: Double,
    val reduced: Double,
    val more: List<Double>,
    val less: List<Double>,
    val final: Double
)
