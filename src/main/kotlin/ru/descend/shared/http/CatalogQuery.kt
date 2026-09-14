package ru.descend.shared.http

import com.mongodb.client.model.Filters
import io.ktor.http.Parameters
import org.bson.conversions.Bson
import java.util.regex.Pattern

/** Literal bounded search; no client supplied Mongo operators or field paths. */
object CatalogQuery {
    fun filter(query: Parameters, kind: String): Bson {
        val filters = mutableListOf<Bson>()
        query["q"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if(it.length > 100) invalid("Search is too long")
            val fields = if(kind == "user") listOf("name", "login") else if(kind == "redemptioncodes") listOf("code", "description") else listOf("name", "description")
            filters += Filters.or(fields.map { field -> Filters.regex(field, Pattern.compile(Pattern.quote(it), Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)) })
        }
        for(key in listOf("slot", "rarity")) query[key]?.takeIf { it.isNotBlank() }?.let {
            if(kind != "equipment" || !it.matches(Regex("[A-Z_]{1,30}"))) invalid("Invalid equipment filter")
            filters += Filters.eq(key, it)
        }
        for((key, operator) in listOf("minLevel" to true, "maxLevel" to false)) query[key]?.takeIf { it.isNotBlank() }?.let {
            val level = it.toIntOrNull()?.takeIf { n -> n in 1..100 } ?: invalid("Invalid level filter")
            if(kind != "equipment") invalid("Level filter requires equipment")
            filters += if(operator) Filters.gte("itemLevel", level) else Filters.lte("itemLevel", level)
        }
        query["stat"]?.takeIf { it.isNotBlank() }?.let { stat ->
            if(kind != "equipment" || stat !in setOf("damage_min", "damage_max", "defense", "attackSpeed")) invalid("Invalid property filter")
            val minimum = query["minStat"]?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: invalid("Invalid property threshold")
            filters += Filters.gte(stat, minimum)
        }
        query["modifierId"]?.takeIf { it.isNotBlank() }?.let {
            if(kind != "equipment" || it.length > 200) invalid("Invalid modifier filter")
            filters += Filters.or(Filters.eq("modifierDefinitionRefs.definitionId", it), Filters.eq("stockModifierDefinitionRefs.definitionId", it))
        }
        return if(filters.isEmpty()) Filters.empty() else Filters.and(filters)
    }
}
