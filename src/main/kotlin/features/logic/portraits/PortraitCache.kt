package features.logic.portraits

import config.ProgressionSeeder
import extensions.printLog
import features.logic.campaign.CampaignContent
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * Манифест `portraits/index.json`: отпечаток каждого портрета по его ключу.
 *
 * Портреты тяжелее иконок и нужны не все сразу, поэтому у каждого свой отпечаток: клиент
 * качает только те файлы, чей отпечаток разошёлся с сохранённым.
 *
 * @property hash отпечаток всего набора - меняется, когда меняется любой файл
 * @property portraits ключ (`class.WITCH`, `form.BAT`, `monster.FALLEN`) - отпечаток файла
 */
@Serializable
data class PortraitManifest(
    val hash: String,
    val width: Int = PortraitCache.WIDTH,
    val height: Int = PortraitCache.HEIGHT,
    val portraits: Map<String, String>,
)

/**
 * Портреты классов и монстров (с 0.29.0): SVG-файлы в ресурсах, по одному на портрет.
 *
 * Лежат по разделам, как ключи словаря: `portraits/class/<CODE>.svg` - портрет класса,
 * `portraits/form/<FORM>.svg` - общий портрет формы монстра, `portraits/monster/<CODE>.svg` -
 * свой портрет одного монстра, который клиент предпочитает портрету его формы. Всё рисуется
 * контурами в поле 300 на 400 (три на четыре), лицо - в круге (150, 165) радиусом 120: из
 * него клиент вырезает круглый жетон для карты.
 *
 * Какие файлы искать, сервер знает сам - из классов и кампании, - поэтому забыть внести
 * портрет в список нельзя: файл либо лежит под своим кодом, либо его нет. Отпечатки
 * считаются из файлов при старте. Клиент рисует SVG сам, поэтому набор элементов узкий:
 * `PortraitTest` проверяет, что каждый файл обходится без того, чего клиент не умеет.
 */
object PortraitCache {

    const val FOLDER = "portraits"
    const val MANIFEST = "index.json"
    const val WIDTH = 300
    const val HEIGHT = 400

    const val CLASS = "class"
    const val FORM = "form"
    const val MONSTER = "monster"

    private var files: Map<String, String> = emptyMap()
    private var hashes: Map<String, String> = emptyMap()
    private var fingerprint: String = ""

    /** Все ключи, под которыми сервер ищет файл: классы, формы монстров и сами монстры. */
    fun candidates(): List<String> =
        ProgressionSeeder.classCodes.map { "$CLASS.$it" } +
            CampaignContent.monsters.values.map { it.form }.distinct().map { "$FORM.$it" } +
            CampaignContent.monsters.keys.map { "$MONSTER.$it" }

    fun manifest(): PortraitManifest = PortraitManifest(fingerprint, portraits = hashes)

    /** Тело одного портрета или null, если такого ключа нет. */
    fun document(section: String, code: String): String? = files["$section.$code"]

    fun keys(): Set<String> = files.keys

    fun initializeCache() {
        files = candidates().mapNotNull { key -> read(key)?.let { key to it } }.toMap()
        hashes = files.mapValues { sha256(it.value) }
        fingerprint = sha256(hashes.toSortedMap().entries.joinToString("\n") { "${it.key}=${it.value}" })
        printLog("[PortraitCache] initialized: portraits=${files.size} hash=$fingerprint")
    }

    fun path(key: String): String = "$FOLDER/${key.substringBefore('.')}/${key.substringAfter('.')}.svg"

    private fun read(key: String): String? =
        javaClass.classLoader.getResourceAsStream(path(key))?.bufferedReader()?.use { it.readText() }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
}
