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
- [x] Игровые константы в JSON контента: `merchant.resaleShare`, `run.packChance`/`packMax`, `combat.ailmentShare`/`attackSpeedMin`/`attackSpeedMax`/`recentSeconds`, `traits.skillMana`, `skills.rules.boostedMaxLevel`/`heroMaxLevel`, `atlas.respawnCap`, `professions.rules.awayMinMinutes`. Поля обязательны - значений по умолчанию в Kotlin нет.
- [x] Остались в коде как структурные, не балансные: `SkillRules.MAX_LEVEL` (число ступеней шкалы умения), `ACTIVE/PASSIVE/CHARGE_SKILLS_PER_CLASS` (проверка состава контента), `StashRules.HARD_CAP` (защита документа), `LEVELS_PER_TIER`/`MAX_TIER` (коды сундуков `_T<n>`), `MIN_UNIQUE_EFFECTS` (проверка контента), `Run.PACK_SLOTS` (кодирование жетона).
- [x] Инфраструктурное - `ServerSettings.fromEnv()` (сроки сессий и почты, лимиты журнала, античит, шаги отметок, окно тестирования, потолок уровня гильдии); `Millis` - один источник единиц времени; `Plausibility` - класс с настройками.
- [x] Сиды забегов и испытаний - через `Dice.system()`; «сейчас» в TrialService - через `ServerClock` из графа.
- [ ] Клиент: `Condition.RECENT` → `rules.recentSeconds`, `SkillRules.MAX_BOOSTED_LEVEL` → `skillRules.rules.boostedMaxLevel` (этап 6 клиента).

## Этап 5 - чистка
- [ ] Удалить `funExceptionClassOnly` (ST_021) с ключами локали.
- [ ] Удалить этот файл.
