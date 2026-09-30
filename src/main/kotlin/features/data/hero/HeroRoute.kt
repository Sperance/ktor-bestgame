package features.data.hero

import base.exception.BaseRouteExceptions
import base.exception.model.SkillExceptions
import base.exception.model.SkillTreeExceptions
import base.route.BaseRoute
import base.route.Crud
import base.route.heroId
import base.route.itemId
import base.route.mapCode
import base.route.optionalParam
import base.route.queryParam
import base.route.respondOk
import com.sperance.exileforge.rules.content.Rarity
import com.sperance.exileforge.rules.content.SkillKind
import com.sperance.exileforge.rules.content.Slot
import com.sperance.exileforge.rules.content.SlotCondition
import com.sperance.exileforge.rules.roll.Dice
import com.sperance.exileforge.rules.roll.ItemFactory
import com.sperance.exileforge.rules.run.RunEvent
import config.ContentStore
import features.logic.atlas.AtlasService
import features.logic.campaign.CampaignService
import features.logic.crafts.CraftsService
import features.logic.hero.HeroSnapshots
import features.logic.hero.Rewards
import features.logic.hero.Stash
import features.logic.hero.respondWithHero
import features.logic.inventory.InventoryService
import features.logic.pets.PetService
import features.logic.quests.QuestService
import features.logic.skills.SkillService
import features.logic.trade.MerchantService
import features.logic.tree.TreeService
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/** Маршруты героя: общий CRUD документа и все игровые команды под `/api/v1/hero`. */
class HeroRoute(
    private val repo: HeroRepository,
    private val content: ContentStore,
    private val inventory: InventoryService,
    private val tree: TreeService,
    private val atlas: AtlasService,
    private val skills: SkillService,
    private val crafts: CraftsService,
    private val merchant: MerchantService,
    private val campaign: CampaignService,
    private val pets: PetService,
    private val quests: QuestService,
) : BaseRoute<Hero>(
    repository = repo,
    entitySerializer = Hero.serializer(),
    operations = setOf(Crud.READ, Crud.COUNT, Crud.CREATE, Crud.UPDATE, Crud.DELETE),
) {
    /** Герой клиенту - без семени наград (1.30.0): его не видит никто, кроме сервера. */
    override fun present(entity: Hero): Hero = entity.copy(rewards = RewardStream())

    override fun additionalRoutes(route: Route) = with(route) {
        get("/byUser") {
            call.respondOk(repo.findByUser(call.queryParam("userId")).map(::present))
        }

        // Герой одним запросом - тот же снимок, что приходит в ответ команды, с ETag.
        get("/view") {
            val snapshot = HeroSnapshots.of(call.heroId, HeroSnapshots.known(call.request.headers[HeroSnapshots.HEADER]))
            val etag = "\"${snapshot.version}\""
            call.response.header(HttpHeaders.ETag, etag)
            if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) call.respond(HttpStatusCode.NotModified)
            else call.respondOk(snapshot)
        }

        // Администратор выдаёт из ничего: опыт, стопку, вещь по шаблону.
        route("/grant") {
            post("/experience") {
                val hero = repo.requireHero(call.heroId, "grantExperience")
                Rewards.addExperience(hero, call.queryParam("amount", 0.0).coerceAtLeast(0.0), content.index)
                call.respondWithHero(repo.save(hero, "grantExperience").level)
            }
            post("/item") {
                val hero = repo.requireHero(call.heroId, "grantItem")
                val code = call.queryParam("code")
                content.index.item(code) ?: throw base.exception.model.CharacterExceptions.funExceptionItemNotFound("grantItem", code)
                hero.earn(code, call.queryParam("amount", 1L), content.index.rules.maxStack)
                call.respondWithHero(repo.save(hero, "grantItem").bag)
            }
            post("/equipment") {
                val hero = repo.requireHero(call.heroId, "grantEquipment")
                val code = call.queryParam("template")
                val template = content.index.template(code) ?: throw base.exception.model.CharacterExceptions.funExceptionEquipmentNotFound("grantEquipment", code)
                val rarity = call.optionalParam("rarity")?.let { Rarity.of(it) } ?: template.rarity
                val item = ItemFactory(content.index).create(Hero.newItemId(), template, rarity, Dice.system(), level = content.index.rules.loot.itemLevel(hero.level))
                Stash.receive(hero, item, content.index)
                repo.save(hero, "grantEquipment")
                call.respondWithHero(item)
            }
        }

        // Вещи: надеть, снять, гнездо, продать, сферы, эссенции, верстак.
        post("/equip") {
            val slot = call.optionalParam("slot")?.let { Slot.of(it) }
            call.respondWithHero(inventory.equip(call.heroId, call.itemId, slot))
        }
        post("/unequip") { call.respondWithHero(inventory.unequip(call.heroId, call.itemId)) }
        post("/socket") { call.respondWithHero(inventory.socket(call.heroId, call.itemId, call.queryParam("nodeCode"))) }
        post("/unsocket") { call.respondWithHero(inventory.unsocket(call.heroId, call.itemId)) }
        post("/sell") { call.respondWithHero(inventory.sell(call.heroId, call.itemId)) }
        post("/orb") { call.respondWithHero(inventory.applyOrb(call.heroId, call.itemId, call.queryParam("orb"), call.optionalParam("omen"))) }
        post("/unveil") {
            val choice = call.queryParam("choice").toIntOrNull() ?: throw BaseRouteExceptions.funExceptionQuery("unveil", "choice")
            call.respondWithHero(inventory.unveil(call.heroId, call.itemId, choice))
        }
        post("/essence") { call.respondWithHero(inventory.applyEssence(call.heroId, call.itemId, call.queryParam("essence"))) }
        get("/bench") { call.respondOk(inventory.bench(repo.requireHero(call.heroId, "bench"))) }
        post("/craft") { call.respondWithHero(inventory.craft(call.heroId, call.itemId, call.queryParam("recipe"))) }
        post("/uncraft") { call.respondWithHero(inventory.uncraft(call.heroId, call.itemId)) }

        // Замок на вещь (1.28.0): `locked=true|false`, в тайнике, надетую или в переполнении.
        route("/item") {
            post("/lock") {
                val locked = call.queryParam("locked").toBooleanStrictOrNull() ?: throw BaseRouteExceptions.funExceptionQuery("lock", "locked")
                call.respondWithHero(inventory.lock(call.heroId, call.itemId, locked))
            }
        }

        // Титул у имени: только из открытых достижениями, пустой - снять.
        post("/title") {
            val hero = repo.requireHero(call.heroId, "title")
            val title = call.optionalParam("title").orEmpty()
            if (title.isNotBlank() && title !in content.index.achievements.titles(hero.chronicle()))
                throw base.exception.model.CharacterExceptions.funExceptionTitleLocked("title", title)
            hero.title = title
            call.respondWithHero(repo.save(hero, "title").title)
        }

        // Тайник: места, докупка пачек, переполнение - забрать или продать.
        route("/stash") {
            get { call.respondOk(inventory.stash(call.heroId)) }
            post("/expand") { call.respondWithHero(inventory.expandStash(call.heroId)) }
            post("/claim") { call.respondWithHero(inventory.claimOverflow(call.heroId, call.optionalParam("itemId"))) }
            post("/sell") { call.respondWithHero(inventory.sellOverflow(call.heroId, call.itemId)) }
        }

        // Зверинец (1.5.0): вылупить яйцо, сфера питомцев, в дело или с места, отпустить за золото.
        route("/pets") {
            get { call.respondOk(pets.state(call.heroId)) }
            post("/hatch") { call.respondWithHero(pets.hatch(call.heroId, call.queryParam("egg"))) }
            post("/orb") { call.respondWithHero(pets.orb(call.heroId, call.queryParam("petId"), call.queryParam("orb"))) }
            post("/activate") { call.respondWithHero(pets.activate(call.heroId, call.queryParam("petId"))) }
            post("/release") { call.respondWithHero(pets.release(call.heroId, call.queryParam("petId"))) }
        }

        // Ремёсла: работа идёт на сервере по времени и досчитывается при каждом обращении.
        route("/crafts") {
            get { call.respondOk(crafts.state(call.heroId)) }
            post("/start") {
                val additives = call.request.queryParameters["additives"]?.split(',').orEmpty()
                call.respondWithHero(crafts.start(call.heroId, call.queryParam("job"), additives))
            }
            post("/stop") { call.respondWithHero(crafts.stop(call.heroId)) }
        }

        // Торговец: витрина героя раз в окно правил, покупка с неё и полка сфер за золото.
        route("/merchant") {
            get { call.respondOk(merchant.stock(call.heroId)) }
            post("/buy") { call.respondWithHero(merchant.buy(call.heroId, call.queryParam("offerId"))) }
            post("/buyOrb") { call.respondWithHero(merchant.buyOrb(call.heroId, call.queryParam("code"))) }
        }

        // Задания (1.21.0): доска героя, награда, всё выполненное разом (1.22.0), контракты с доски.
        route("/quests") {
            get { call.respondOk(quests.board(call.heroId)) }
            post("/claim") { call.respondWithHero(quests.claim(call.heroId, call.queryParam("questId"))) }
            post("/claimAll") { call.respondWithHero(quests.claimAll(call.heroId)) }
            post("/take") { call.respondWithHero(quests.take(call.heroId, call.queryParam("offerId"))) }
            post("/abandon") { call.respondWithHero(quests.abandon(call.heroId, call.queryParam("questId"))) }
        }

        // Кампания по семени: вход выдаёт семя и контекст, журнал событий проигрывается сервером.
        route("/campaign") {
            get("/progress") { call.respondOk(campaign.progress(call.heroId)) }
            post("/start") { call.respondWithHero(campaign.start(call.heroId, call.mapCode, call.optionalParam("itemId"))) }
            post("/events") { call.respondWithHero(campaign.events(call.heroId, call.receive<List<RunEvent>>())) }
        }

        // Умения класса: книга учит уровень, слоты с условиями, условия глотков фляг, обмен книг.
        route("/skills") {
            post("/learn") { call.respondWithHero(skills.learn(call.heroId, call.queryParam("skill"))) }
            post("/slot") {
                val kind = call.queryParam("kind").let { name -> SkillKind.entries.firstOrNull { it.name == name } }
                    ?: throw SkillExceptions.funExceptionSlot("slot", call.queryParam("kind"))
                call.respondWithHero(skills.slot(call.heroId, kind, call.queryParam("index", -1), call.optionalParam("skill"), call.optionalParam("condition")?.let(::condition)))
            }
            post("/flask") { call.respondWithHero(skills.flask(call.heroId, call.queryParam("index", -1), call.optionalParam("condition")?.let(::condition))) }
            post("/exchange") {
                val books = call.queryParam("books").split(',').map { it.trim() }.filter { it.isNotEmpty() }
                call.respondWithHero(skills.exchange(call.heroId, books, call.queryParam("skill")))
            }
        }

        route("/skilltree") {
            get("/state") { call.respondOk(tree.state(call.heroId)) }
            post("/allocate") {
                val choice = call.request.queryParameters["choice"]?.let { it.toIntOrNull() ?: throw SkillTreeExceptions.funExceptionChoice("allocate", it) }
                call.respondWithHero(tree.allocate(call.heroId, call.queryParam("nodeCode"), choice))
            }
            post("/path") {
                val choice = call.request.queryParameters["choice"]?.let { it.toIntOrNull() ?: throw SkillTreeExceptions.funExceptionChoice("allocatePath", it) }
                call.respondWithHero(tree.allocatePath(call.heroId, call.queryParam("nodeCode"), choice))
            }
            post("/refund") { call.respondWithHero(tree.refund(call.heroId, call.queryParam("nodeCode"))) }
            post("/rechoose") {
                val choice = call.queryParam("choice").let { it.toIntOrNull() ?: throw SkillTreeExceptions.funExceptionChoice("rechoose", it) }
                call.respondWithHero(tree.rechoose(call.heroId, call.queryParam("nodeCode"), choice))
            }
            post("/reset") { call.respondWithHero(tree.reset(call.heroId)) }
        }

        route("/atlas") {
            get("/state") { call.respondOk(atlas.state(call.heroId)) }
            post("/allocate") { call.respondWithHero(atlas.allocate(call.heroId, call.queryParam("nodeCode"))) }
            post("/refund") { call.respondWithHero(atlas.refund(call.heroId, call.queryParam("nodeCode"))) }
            post("/reset") { call.respondWithHero(atlas.reset(call.heroId)) }
        }
    }
}

/** Условие слота по имени; неизвестное - отказ, а не тихое «как готово». */
private fun condition(name: String): SlotCondition =
    SlotCondition.entries.firstOrNull { it.name == name } ?: throw SkillExceptions.funExceptionCondition("condition", name)
