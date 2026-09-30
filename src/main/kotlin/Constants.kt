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
const val SERVER_VERSION = "1.54.0"

/**
 * Ревизия контракта с клиентом. Растёт, когда клиент обязан перейти на новые маршруты или схему
 * контента; история ревизий - в CHANGELOG. 25 - без свитков зачарования и варианта `ENCHANT` (1.42.0). 26 - одна карта `MAP`
 * с зоной `mz`, работы с выбором (`choice`, `options`, `JobKind.CONDENSE`), регион картографа, редкость `MAGIC` (1.43.0).
 * 27 - `JobKind.REFINE`/`JEWEL`, выбор кузнеца, поля работ `chain`/`ratio`/`tier` (1.44.0).
 * 28 - план дерева `skilltree/plan`, фильтр добычи `hero/autosell`, места аукциона без докупки (без `POST auctionlot/slots`) (1.45.0).
 * 29 - секрет устройства от сервера (`user/byDeviceId` без тела отвечает `deviceSecret`, вход - по секрету), отчёты
 *      об ошибках `POST bugreport`, 403 `AUTH_006` блокировки (1.46.0).
 * 30 - испытания: `hero/trials/rush|tower|events`, `campaign.trials` героя, раздел `trials` кампании (1.47.0).
 * 31 - ключ раша `RUSH_KEY`: `POST hero/trials/key` собирает его из фрагментов, раш тратит ключ (1.48.0).
 * 32 - статистика героя: события `FIGHT` с итогом боя `fight`, `GET hero/stats`, новые счётчики летописи (1.49.0).
 * 33 - захваченные карты: `ActiveMap.influence`, сферы Создателя и Древнего на карте, ветка атласа «Влияние», атлас веером (1.50.0).
 * 34 - задания без зон (`Quest.zones`, `QuestScope`, `HIGH_ZONE` сняты), `POST hero/skilltree/refundBranch`, `FightTally.killer` (1.52.0).
 * 35 - повтор по `Idempotency-Key` без тела (`data: null`), журнал не больше 64 событий (422 `CP_022`), 503 `BRY_007` при
 *      недоступной базе, `WorkView.seed` всегда 0, потолок башни `tower.maxFloor`, отказ без `errorClass`/`errorMethod` (1.53.0).
 */
const val API_REVISION = 35

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

/**
 * Доверенные прокси (1.53.0): адреса, с которых `X-Forwarded-For` / `X-Real-IP` считаются адресом игрока, через запятую;
 * `*` - любой. Не задано - заголовки не читаются, и за прокси все игроки были бы одним адресом для лимитов и блок-листа.
 */
val TRUSTED_PROXIES: List<String> = env("TRUSTED_PROXIES")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

/** Разовая очистка базы (1.53.0) только по явному флагу: `WIPE_DB` равен метке [config.DatabaseWipe.MARKER]. */
val WIPE_DB: String? = env("WIPE_DB")

/** Адреса, которым разрешён доступ из браузера (CORS), через запятую. Не задано - никому. */
val CORS_HOSTS: List<String> = env("CORS_HOSTS")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
