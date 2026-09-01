package features.data.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumModifierOperation
import application.enums.EnumModifierSource
import application.enums.EnumRarity
import application.enums.EnumStatStock
import features.logic.modifiers.Modifier

object EquipmentSeeder {

    fun seed(): ArrayList<Equipment> {
        val list = ArrayList<Equipment>()

        seedHelmets(list)

        return list
    }

    private fun seedHelmets(list: ArrayList<Equipment>) {
        // ==================== COMMON HELMETS ====================
        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 12,
            name = "Leather Cap",
            rarity = EnumRarity.COMMON,
            itemLevel = 1,
        ).apply { this.description = "A simple leather headguard." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 15,
            name = "Iron Skullcap",
            rarity = EnumRarity.COMMON,
            itemLevel = 5,
        ).apply { this.description = "Light iron cap for basic protection." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 18,
            name = "Hide Helm",
            rarity = EnumRarity.COMMON,
            itemLevel = 10,
        ).apply { this.description = "Reinforced hide headgear." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 20,
            name = "Chain Coif",
            rarity = EnumRarity.COMMON,
            itemLevel = 15,
        ).apply { this.description = "Basic chainmail hood." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 22,
            name = "Sallet",
            rarity = EnumRarity.COMMON,
            itemLevel = 20,
        ).apply { this.description = "Standard military helmet." })

        // ==================== UNCOMMON HELMETS ====================
        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 28,
            name = "Steel Helm",
            rarity = EnumRarity.UNCOMMON,
            itemLevel = 25,
        ).apply { this.description = "Sturdy steel helmet." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 32,
            name = "Knight's Casque",
            rarity = EnumRarity.UNCOMMON,
            itemLevel = 30,
        ).apply { this.description = "Full-face knight helmet." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 36,
            name = "Bronze Greathelm",
            rarity = EnumRarity.UNCOMMON,
            itemLevel = 35,
        ).apply { this.description = "Heavy bronze great helm." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 40,
            name = "Visored Helm",
            rarity = EnumRarity.UNCOMMON,
            itemLevel = 40,
        ).apply { this.description = "Helm with protective visor." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 44,
            name = "Warden's Crown",
            rarity = EnumRarity.UNCOMMON,
            itemLevel = 45,
        ).apply { this.description = "Guardian's protective crown." })

        // ==================== RARE HELMETS ====================
        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 52,
            name = "Helm of Valor",
            rarity = EnumRarity.RARE,
            itemLevel = 50,
        ).apply { this.description = "Forged for brave warriors." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 58,
            name = "Battle Mask",
            rarity = EnumRarity.RARE,
            itemLevel = 55,
        ).apply { this.description = "Intimidating battle mask." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 64,
            name = "Aegis Helm",
            rarity = EnumRarity.RARE,
            itemLevel = 60,
        ).apply { this.description = "Shield-protected helm." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 70,
            name = "Fury's Visage",
            rarity = EnumRarity.RARE,
            itemLevel = 65,
        ).apply { this.description = "Mask of the raging warrior." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 76,
            name = "Warrior's Sallet",
            rarity = EnumRarity.RARE,
            itemLevel = 70,
        ).apply { this.description = "Elite warrior's sallet." })

        // ==================== EPIC HELMETS ====================
        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 88,
            name = "Helm of Justice",
            rarity = EnumRarity.EPIC,
            itemLevel = 75,
        ).apply { this.description = "Blessed helm of righteous warriors." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 96,
            name = "Crown of Glory",
            rarity = EnumRarity.EPIC,
            itemLevel = 80,
        ).apply { this.description = "Crown worn by legendary champions." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 104,
            name = "Helm of the Martyr",
            rarity = EnumRarity.EPIC,
            itemLevel = 85,
        ).apply { this.description = "Forged in sacrifice and pain." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 112,
            name = "Radiant Casque",
            rarity = EnumRarity.EPIC,
            itemLevel = 90,
        ).apply { this.description = "Shining with inner light." })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 120,
            name = "Helm of Enlightenment",
            rarity = EnumRarity.EPIC,
            itemLevel = 95,
        ).apply { this.description = "Grants clarity of mind and body." })

        // ==================== LEGENDARY HELMETS ====================
        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 138,
            name = "Azure Crown",
            rarity = EnumRarity.LEGENDARY,
            itemLevel = 4,
        ).apply {
            this.description = "Crown of the azure kings."
            this.implicitModifiers = arrayListOf(
                Modifier(EnumStatStock.STOCK_HEALTH, EnumModifierOperation.ADD, 25.0, EnumModifierSource.PREFIX),
                Modifier(EnumStatStock.STOCK_CRITICAL_CHANCE, EnumModifierOperation.ADD, 8.0, EnumModifierSource.PREFIX),
            )
        })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 150,
            name = "Helm of the Titan",
            rarity = EnumRarity.LEGENDARY,
            itemLevel = 16,
        ).apply {
            this.description = "Forged in the heart of a mountain."
            this.implicitModifiers = arrayListOf(
                Modifier(EnumStatStock.STOCK_ARMOR, EnumModifierOperation.ADD, 600.0, EnumModifierSource.PREFIX),
            )
        })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 162,
            name = "Dragonlord's Helm",
            rarity = EnumRarity.LEGENDARY,
            itemLevel = 72,
        ).apply {
            this.description = "Worn by dragon masters."
            this.implicitModifiers = arrayListOf(
                Modifier(EnumStatStock.STOCK_ATTACK_FIRE, EnumModifierOperation.ADD, 55.0, EnumModifierSource.PREFIX),
                Modifier(EnumStatStock.STOCK_RESIST_FIRE, EnumModifierOperation.MORE, 20.0, EnumModifierSource.PREFIX),
            )
        })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 174,
            name = "Helm of Immortality",
            rarity = EnumRarity.LEGENDARY,
            itemLevel = 44,
        ).apply {
            this.description = "Grants eternal vitality."
            this.implicitModifiers = arrayListOf(
                Modifier(EnumStatStock.STOCK_HEALTH, EnumModifierOperation.ADD, 350.0, EnumModifierSource.PREFIX),
                Modifier(EnumStatStock.STOCK_HEALTH_REGEN, EnumModifierOperation.MORE, 8.0, EnumModifierSource.PREFIX),
            )
        })

        list.add(Armor(
            slot = EnumEquipmentType.HELMET,
            defense = 186,
            name = "Legendary Casque",
            rarity = EnumRarity.LEGENDARY,
            itemLevel = 90,
        ).apply {
            this.description = "The ultimate head protection."
            this.implicitModifiers = arrayListOf(
                Modifier(EnumStatStock.STOCK_ARMOR, EnumModifierOperation.MORE, 15.0, EnumModifierSource.PREFIX),
                Modifier(EnumStatStock.STOCK_MANA, EnumModifierOperation.ADD, 470.0, EnumModifierSource.PREFIX),
                Modifier(EnumStatStock.STOCK_BLOCK_CHANCE, EnumModifierOperation.MORE, 5.0, EnumModifierSource.PREFIX),
            )
        })
    }
}