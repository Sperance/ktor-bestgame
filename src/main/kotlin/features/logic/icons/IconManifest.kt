package features.logic.icons

import kotlinx.serialization.Serializable

/**
 * Манифест `icons/index.json`: отпечаток набора и что в нём лежит.
 *
 * Отдаётся отдельно от тела, потому что тело весит на два порядка больше:
 * клиент читает манифест на каждом старте, а тело - только когда
 * [hash] разошёлся с сохранённым.
 *
 * @property hash отпечаток тела, считается сервером из самого файла
 * @property file имя файла с телом внутри той же папки
 * @property sprites сколько в наборе рисунков
 * @property icons сколько кодов на них ссылается
 */
@Serializable
data class IconManifest(
    val hash: String,
    val file: String,
    val sprites: Int,
    val icons: Int,
)
