package ru.descend.features.combat.domain

/** Small reviewed starter world. Mongo stores the active catalog; seeding never overwrites it. */
object CombatWorld {
    val initial = CombatCatalog(zones = listOf(
        Zone("coast", "Пепельный берег", "Мертвецы и падальщики у разбитых кораблей.", 1,
            listOf(Monster("drowned", "Утопленник", 24.0, 3.0, lootTableId = "common", experience = 15, gold = 5),
                Monster("scavenger", "Падальщик", 20.0, 4.0, evasion = 5.0, lootTableId = "common", experience = 18, gold = 6)),
            Monster("keeper", "Хранитель обломков", 80.0, 7.0, armour = 12.0, lootTableId = "boss", experience = 90, gold = 35, boss = true)),
        Zone("crypt", "Затопленный склеп", "Холодные залы, населённые стражами и призраками.", 5,
            listOf(Monster("guard", "Страж склепа", 75.0, 10.0, armour = 35.0, lootTableId = "uncommon", experience = 40, gold = 12),
                Monster("wraith", "Ледяной призрак", 65.0, 12.0, element = "cold", lootTableId = "uncommon", experience = 45, gold = 14)),
            Monster("abbot", "Проклятый настоятель", 220.0, 20.0, element = "cold", lootTableId = "boss", experience = 220, gold = 80, boss = true)),
        Zone("citadel", "Угольная цитадель", "Огненные твари и последний владыка крепости.", 12,
            listOf(Monster("hound", "Пепельная гончая", 150.0, 22.0, evasion = 30.0, lootTableId = "uncommon", experience = 95, gold = 25),
                Monster("knight", "Обугленный рыцарь", 190.0, 20.0, armour = 100.0, lootTableId = "uncommon", experience = 110, gold = 30)),
            Monster("sovereign", "Угольный владыка", 550.0, 38.0, armour = 100.0, element = "fire", lootTableId = "boss", experience = 600, gold = 200, boss = true))
    ), lootTables = listOf(
        LootTable("common", 1, listOf(LootEntry("NONE", 35), LootEntry("NORMAL", 35), LootEntry("MAGIC", 15), LootEntry("TRANSMUTATION", 15))),
        LootTable("uncommon", 1, listOf(LootEntry("NORMAL", 20), LootEntry("MAGIC", 40), LootEntry("RARE", 15), LootEntry("ALTERATION", 25))),
        LootTable("boss", 2, listOf(LootEntry("MAGIC", 30), LootEntry("RARE", 40), LootEntry("UNIQUE", 5), LootEntry("ALCHEMY", 15), LootEntry("CHAOS", 10)))
    ))
    fun validate(catalog: CombatCatalog) {
        require(catalog.zones.size in 1..100 && catalog.lootTables.size in 1..100)
        require(catalog.zones.map { it.id }.distinct().size == catalog.zones.size)
        require(catalog.lootTables.map { it.id }.distinct().size == catalog.lootTables.size)
        catalog.lootTables.forEach { table ->
            require(table.rolls in 1..2 && table.entries.size in 1..50)
            require(table.entries.sumOf { it.weight.toLong() } <= 100000)
            table.entries.forEach { require(it.weight in 1..10000 && it.amount in 1..10 && it.kind in setOf("NONE", "NORMAL", "MAGIC", "RARE", "UNIQUE", "TRANSMUTATION", "ALTERATION", "ALCHEMY", "CHAOS")) }
        }
        catalog.zones.forEach { zone ->
            require(zone.id.matches(Regex("[a-z0-9_-]{1,40}")) && zone.level in 1..100 && zone.killsForBoss in 1..100 && zone.monsters.size in 1..20)
            require(zone.boss.boss && zone.monsters.none { it.boss })
            (zone.monsters + zone.boss).forEach { m ->
                require(m.life.isFinite() && m.life in 1.0..1000000.0 && m.damage.isFinite() && m.damage in 1.0..100000.0)
                require(m.armour.isFinite() && m.armour in 0.0..1000000.0 && m.evasion.isFinite() && m.evasion in 0.0..1000000.0)
                require(m.element in setOf("physical", "fire", "cold", "lightning", "chaos"))
                require(m.experience in 0..100000 && m.gold in 0..100000 && catalog.lootTables.any { it.id == m.lootTableId })
            }
        }
    }
}
