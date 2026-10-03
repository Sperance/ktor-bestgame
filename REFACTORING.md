# REFACTORING.md - план рефакторинга сервера

Временный документ: этапы и прогресс. Читай в начале каждой задачи по рефакторингу, отмечай сделанное, удали по завершении. Пуш и подъём версии - по завершённым этапам; сервер всегда раньше клиента. Решения зафиксированы в `RULES.md`.

## Этап 1 - пакеты и запуск
- [x] Все исходники в `ru.descend.exileforge.*` (структура папок = пакеты), тесты тоже.
- [x] `mainClass` = `ru.descend.exileforge.ApplicationKt`; мёртвые `application.yaml` и `ktor-server-config-yaml` удалены.
- [x] Скрипт клиента `scripts/client_server_test.py` запускает новый класс.

## Этап 2 - слои и DI
- [x] Репозиторий = только CRUD; логика в `features/logic/<x>`: HeroService, UserService, MailService, FeedbackService, RedemptionService, AuctionService, Guild{Service,Tree,Stash,Quest}Service + GuildAccess/GuildChange, QuestEngine (правила) и QuestService (команды).
- [x] Все зависимости через конструктор; Koin спрашивается только в `Modules.kt` (`singleOf`) и в точке сборки `Application.kt`.
- [x] `HeroRunStore`, `HeroStatsStore`, `RouteTimings` - классы графа с базой в конструкторе.
- [x] Мутации `Hero` только через методы-операции (`gain`, `pay`, `earn`, `spend`, `gainExperience`, `loseExperience`, `setLevel`, `grantPoints`, `expandStash`).
- [x] `KoinGraphTest` собирает граф целиком без базы.

## Этап 3 - типы
- [ ] value class для кодов в `rules/`: `ItemCode`, `ModifierCode`, `MonsterCode`, `MapCode` (`@JvmInline @Serializable`); статы - типизированный реестр вместо строк `"STOCK_*"`.
- [ ] Sealed вместо `kind` + nullable: `Reward`, `LotGoods`, `CraftJob`, `RunEvent`, `TrialEvent` - полиморфная сериализация с дискриминатором `kind`; база сбрасывается; `API_REVISION` +1.
- [ ] Парсинг enum из строк в роутах - общий хелпер, не `entries.firstOrNull { it.name == … }` по месту.

## Этап 4 - баланс и настройки
- [ ] Игровые константы из Kotlin в `content/rules.json` (`EngineRules`): RESALE_SHARE, AILMENT_SHARE, лимиты умений и уровней, PACK_CHANCE/PACK_MAX, TRAIT_MANA, RESPAWN_CAP, LEVELS_PER_TIER/MAX_TIER, HARD_CAP, RECENT, MIN_UNIQUE_EFFECTS, MIN_MILLIS.
- [ ] Инфраструктурное - один `ServerSettings` из окружения с дефолтами: срок сессии, MAX_PER_USER, MAX_EVENTS, KILLS_PER_SECOND, MAIL_DAYS, SEEN_STEP, лимиты тестера. `DAY_MS`/`HOUR` - один источник.
- [ ] Случайность только через `Dice`: сиды забегов в CampaignService/TrialService, `System.currentTimeMillis` в TrialService - через `Clock`.

## Этап 5 - чистка
- [ ] Удалить `funExceptionClassOnly` (ST_021) с ключами локали.
- [ ] Удалить этот файл.
