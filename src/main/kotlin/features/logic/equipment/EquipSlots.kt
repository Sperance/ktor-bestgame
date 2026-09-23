package features.logic.equipment

import application.enums.EnumEquipmentType
import application.enums.EnumEquipmentType.QUIVER
import application.enums.EnumEquipmentType.RING
import application.enums.EnumEquipmentType.RING_2
import application.enums.EnumEquipmentType.SHIELD
import application.enums.EnumEquipmentType.WEAPON_1H
import application.enums.EnumEquipmentType.WEAPON_2H
import application.enums.EnumEquipmentWeapon

/**
 * Куда встаёт надеваемый предмет и что он снимает - как руки и кольца в POE.
 *
 * Чистые функции над слотами, без базы: правила проверяются тестом, а репозиторий
 * только применяет ответ. Рук две: двуручное оружие занимает обе, лук стреляет только
 * из колчана, остальное одноручное носят со щитом. Колец два, второе - [RING_2].
 */
object EquipSlots {

    /**
     * Слот, в который встанет предмет.
     *
     * Кольцо идёт в названный слот, если его назвали, иначе в первый свободный, а когда
     * заняты оба - на место первого. Всё остальное встаёт в слот своего шаблона.
     *
     * @param requested слот, который выбрал игрок, - имеет смысл только для кольца
     * @param occupied слоты, которые уже заняты
     */
    fun target(slot: EnumEquipmentType, requested: EnumEquipmentType?, occupied: Collection<EnumEquipmentType>): EnumEquipmentType {
        if (slot != RING) return slot
        if (requested == RING || requested == RING_2) return requested
        return listOf(RING, RING_2).firstOrNull { it !in occupied } ?: RING
    }

    /**
     * Слоты, которые освобождает надеваемый предмет, кроме его собственного.
     *
     * @param weapon вид надеваемого оружия, null у всего остального
     * @param wornWeapon вид одноручного оружия, которое уже надето, null если его нет
     */
    fun displaced(slot: EnumEquipmentType, weapon: EnumEquipmentWeapon?, wornWeapon: EnumEquipmentWeapon?): Set<EnumEquipmentType> {
        val wornBow = wornWeapon == EnumEquipmentWeapon.BOW
        return when (slot) {
            WEAPON_2H -> setOf(WEAPON_1H, SHIELD, QUIVER)
            WEAPON_1H -> if (weapon == EnumEquipmentWeapon.BOW) setOf(WEAPON_2H, SHIELD) else setOf(WEAPON_2H, QUIVER)
            SHIELD -> if (wornBow) setOf(WEAPON_2H, QUIVER, WEAPON_1H) else setOf(WEAPON_2H, QUIVER)
            QUIVER -> if (wornWeapon != null && !wornBow) setOf(WEAPON_2H, SHIELD, WEAPON_1H) else setOf(WEAPON_2H, SHIELD)
            else -> emptySet()
        }
    }
}
