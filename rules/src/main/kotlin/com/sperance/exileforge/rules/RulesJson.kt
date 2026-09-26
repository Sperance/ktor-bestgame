package com.sperance.exileforge.rules

import kotlinx.serialization.json.Json

/**
 * Один JSON на файлы контента, чанки клиента и документы героя: неизвестные ключи не роняют
 * чтение, значения по умолчанию и null не пишутся - документ несёт только то, что отличается.
 */
val RulesJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
    explicitNulls = false
    isLenient = true
}

/** Тот же JSON с отступами - для файлов контента, которые правят руками. */
val PrettyJson: Json = Json(RulesJson) { prettyPrint = true; prettyPrintIndent = "  " }
