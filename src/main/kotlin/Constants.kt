const val CONST_FIELD_ID = "_id"
const val CONST_FIELD_VERSION  = "version"
const val CONST_FIELD_DELETED  = "deleted"
const val CONST_FIELD_UPDATED  = "updatedAt"
val CONST_SYSTEM_FIELDS = listOf("_id", "id", "version", "deleted", "createdAt", "updatedAt")

const val CONST_API_VERSION = 1

/** Размер страницы по умолчанию, если запрос его не задал. */
const val CONST_PAGE_SIZE_DEFAULT = 20

/** Потолок размера страницы: одним запросом всю коллекцию не вычитать. */
const val CONST_PAGE_SIZE_MAX = 100

/**
 * Версия сервера. Отдаётся в `static/index.json`, чтобы клиент мог сверить её с той, под
 * которую собран, а не верить своей константе на слово.
 */
const val SERVER_VERSION = "1.0.1"

/**
 * Ревизия контракта с клиентом. Растёт, когда клиент обязан перейти на новые маршруты: 5 -
 * единый манифест `static/index.json` и снимок героя (0.48.0); 7 - дерево с мастерствами (0.52.0); 8 -
 * ссылки на модификаторы кодом (0.56.0); 9 - страж порчи за Ваал-зоной (0.57.0); 11 - карта мира (0.67.0);
 * 12 - зона целиком в `start` (0.68.1); 13 - мана, умения, фляги и эссенции (0.69.0); 14 - контент файлами
 * и чанками `/content/<файл>`, герой одним документом `hero`, роллы `{c,t,p}`, заход по семени
 * `campaign/start` + `campaign/events` (1.0.0).
 */
const val API_REVISION = 14

/**
 * Настройки развёртывания читаются из окружения, а не из кода: сервер переезжает на другой
 * хост или в тестовую базу без пересборки. Значения по умолчанию - прежние, так что локальный
 * запуск ничего не замечает. Секретов у них по умолчанию нет - см. DatabaseSeeder.
 */
private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val MONGO_URI: String = env("MONGO_URI") ?: "mongodb://localhost:27017"
val MONGO_DB: String = env("MONGO_DB") ?: "mongobase"
val SERVER_PORT: Int = env("PORT")?.toIntOrNull() ?: 8080

/** Пароль сидового администратора. Не задан - администратор не создаётся вовсе. */
val SEED_ADMIN_PASSWORD: String? = env("ADMIN_PASSWORD")

/** Пароль сидового тестового игрока. Не задан - тестовый игрок не создаётся. */
val SEED_TEST_PLAYER_PASSWORD: String? = env("TEST_PLAYER_PASSWORD")

/** Адреса, которым разрешён доступ из браузера (CORS), через запятую. Не задано - никому. */
val CORS_HOSTS: List<String> = env("CORS_HOSTS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
