package com.sperance.exileforge.rules

/**
 * Отказ правила: код ошибки (`CR_003`, `CP_012`...) и аргументы его шаблона в словаре.
 *
 * Правила не знают ни языка, ни HTTP: сервер заворачивает отказ в свой конверт, клиент читает
 * `error.<code>` и подставляет аргументы. Аргумент, который сам является ключом словаря, обе
 * стороны переводят.
 */
class RuleViolation(val code: String, val args: List<String> = emptyList(), message: String = "$code ${args.joinToString(" ")}") : RuntimeException(message)

/** Битый контент: файл не сходится сам с собой. Ошибка старта сервера и сборки клиента, а не тихий откат. */
class ContentException(message: String) : RuntimeException(message)

internal fun fail(what: String): Nothing = throw ContentException(what)
