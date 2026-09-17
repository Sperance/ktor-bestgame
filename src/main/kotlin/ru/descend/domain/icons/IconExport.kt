package ru.descend.domain.icons

import java.io.File

/**
 * Выгрузка набора на диск: отдельные SVG, спрайт и страница предпросмотра.
 *
 * Нужна при правке рисунков — посмотреть весь набор целиком, не поднимая сервер.
 * `./gradlew iconPreview` кладёт результат в `build/icons`.
 */
object IconExport {
    @JvmStatic
    fun main(args: Array<String>) {
        val target = File(args.firstOrNull() ?: "build/icons")
        File(target, "svg").mkdirs()
        IconCatalog.icons.forEach { art ->
            File(target, "svg/${art.id}.svg").writeText(requireNotNull(IconCatalog.svg(art.id)))
        }
        File(target, "sprite.svg").writeText(IconCatalog.sprite)
        File(target, "preview.html").writeText(preview())
        println("Icons: ${IconCatalog.icons.size}, set ${IconRenderer.SET_ID} v${IconRenderer.SET_REVISION} (${IconCatalog.version}) -> ${target.absolutePath}")
    }

    fun preview(): String = buildString {
        append("<!doctype html><html lang=\"ru\"><head><meta charset=\"utf-8\"><title>Иконки ExileForge</title>")
        append("<style>body{background:#0a0d12;color:#cdb68a;font:14px/1.4 system-ui,sans-serif;margin:24px}")
        append("h2{color:#e3bb82;border-bottom:1px solid #3a2f1e;padding-bottom:6px;margin-top:32px}")
        append(".grid{display:flex;flex-wrap:wrap;gap:14px}.cell{width:110px;text-align:center}")
        append(".cell span{display:block;font-size:11px;color:#8b8577;word-break:break-word;margin-top:4px}</style></head><body>")
        append("<h1>${IconRenderer.SET_ID} v${IconRenderer.SET_REVISION} — ${IconCatalog.icons.size} иконок</h1>")
        IconCatalog.categories().forEach { (category, ids) ->
            append("<h2>$category (${ids.size})</h2><div class=\"grid\">")
            ids.forEach { id ->
                append("<div class=\"cell\">${IconCatalog.svg(id)}<span>$id</span></div>")
            }
            append("</div>")
        }
        append("</body></html>")
    }
}
