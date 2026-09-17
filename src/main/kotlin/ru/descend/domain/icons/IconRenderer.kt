package ru.descend.domain.icons

/**
 * Сборка SVG из рисунка иконки.
 *
 * Используются только примитивы, которые одинаково рисуют и браузер,
 * и Android (`AndroidSVG`, `coil-svg`): path, circle, линейный и радиальный градиент.
 * Без фильтров, масок и внешних шрифтов.
 */
object IconRenderer {
    const val VIEW_BOX = 64
    const val SET_ID = "forge-vector"
    const val SET_REVISION = 1

    /** Рамка в стиле чернёной пластины: срезанные углы, бронзовый кант и четыре заклёпки. */
    private const val PLATE = "M6 13 L13 6 H51 L58 13 V51 L51 58 H13 L6 51 Z"
    private const val PLATE_INNER = "M10 14.5 L14.5 10 H49.5 L54 14.5 V49.5 L49.5 54 H14.5 L10 49.5 Z"

    fun svg(art: IconArt, framed: Boolean = true): String = buildString {
        append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $VIEW_BOX $VIEW_BOX" width="$VIEW_BOX" height="$VIEW_BOX" role="img" aria-label="${escape(art.title)}">""")
        append("<title>${escape(art.title)}</title>")
        append(defs(art, art.id, framed))
        append(content(art, art.id, framed))
        append("</svg>")
    }

    /** Один элемент спрайта. Идентификаторы градиентов разведены по иконкам. */
    fun symbol(art: IconArt, framed: Boolean = true): String = buildString {
        append("""<symbol id="icon-${art.id}" viewBox="0 0 $VIEW_BOX $VIEW_BOX"><title>${escape(art.title)}</title>""")
        append(defs(art, art.id, framed))
        append(content(art, art.id, framed))
        append("</symbol>")
    }

    /** Весь набор одним файлом: клиент забирает его один раз и рисует через `<use href="#icon-...">`. */
    fun sprite(icons: List<IconArt>, framed: Boolean = true): String = buildString {
        append("""<svg xmlns="http://www.w3.org/2000/svg" width="0" height="0" style="display:none"><title>ExileForge icon sprite</title>""")
        icons.forEach { append(symbol(it, framed)) }
        append("</svg>")
    }

    /**
     * Градиенты заданы в координатах рисунка (`userSpaceOnUse`).
     *
     * Это не украшательство: при координатах в долях рамки прямая линия имеет нулевую
     * ширину рамки, градиент становится некорректным и штрих просто не рисуется.
     * Из-за этого пропадали рукояти, перекрестья и разделители.
     */
    private fun defs(art: IconArt, suffix: String, framed: Boolean): String = buildString {
        append("<defs>")
        append("""<linearGradient id="edge-$suffix" gradientUnits="userSpaceOnUse" x1="32" y1="6" x2="32" y2="58">""")
        append("""<stop offset="0" stop-color="${art.tint}"/><stop offset="1" stop-color="${art.deep}"/></linearGradient>""")
        append("""<radialGradient id="core-$suffix" gradientUnits="userSpaceOnUse" cx="32" cy="28" r="28">""")
        append("""<stop offset="0" stop-color="${art.tint}" stop-opacity="0.55"/>""")
        append("""<stop offset="1" stop-color="${art.deep}" stop-opacity="0.12"/></radialGradient>""")
        if (framed) {
            append("""<linearGradient id="plate-$suffix" gradientUnits="userSpaceOnUse" x1="10" y1="6" x2="48" y2="58">""")
            append("""<stop offset="0" stop-color="#171d25"/><stop offset="1" stop-color="#080b10"/></linearGradient>""")
            append("""<radialGradient id="halo-$suffix" gradientUnits="userSpaceOnUse" cx="32" cy="30" r="24">""")
            append("""<stop offset="0" stop-color="${art.tint}" stop-opacity="0.22"/>""")
            append("""<stop offset="1" stop-color="${art.tint}" stop-opacity="0"/></radialGradient>""")
        }
        append("</defs>")
    }

    private fun content(art: IconArt, suffix: String, framed: Boolean): String = buildString {
        if (framed) {
            append("""<path d="$PLATE" fill="url(#plate-$suffix)" stroke="${art.deep}" stroke-width="1.6"/>""")
            append("""<circle cx="32" cy="32" r="22" fill="url(#halo-$suffix)"/>""")
            append("""<path d="$PLATE_INNER" fill="none" stroke="${art.tint}" stroke-opacity="0.25" stroke-width="1"/>""")
            listOf(13 to 13, 51 to 13, 13 to 51, 51 to 51).forEach { (x, y) ->
                append("""<circle cx="$x" cy="$y" r="1.3" fill="${art.tint}" fill-opacity="0.45"/>""")
            }
        }
        append("""<g fill="none" stroke="url(#edge-$suffix)" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">""")
        append(art.body.replace("url(#core)", "url(#core-$suffix)"))
        append("</g>")
    }

    private fun escape(value: String) = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
