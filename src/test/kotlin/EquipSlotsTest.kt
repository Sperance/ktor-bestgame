import application.enums.EnumEquipmentType.QUIVER
import application.enums.EnumEquipmentType.RING
import application.enums.EnumEquipmentType.RING_2
import application.enums.EnumEquipmentType.SHIELD
import application.enums.EnumEquipmentType.BOOTS
import application.enums.EnumEquipmentType.WEAPON_1H
import application.enums.EnumEquipmentType.WEAPON_2H
import application.enums.EnumEquipmentWeapon.BOW
import application.enums.EnumEquipmentWeapon.SWORD
import features.logic.equipment.EquipSlots
import org.junit.Test
import kotlin.test.assertEquals

/**
 * Руки и кольца: куда встаёт предмет и что он снимает. Без базы - это чистые правила.
 */
class EquipSlotsTest {

    @Test
    fun a_ring_takes_the_free_place_then_the_chosen_one_then_the_first() {
        assertEquals(RING, EquipSlots.target(RING, null, emptyList()))
        assertEquals(RING_2, EquipSlots.target(RING, null, listOf(RING)))
        assertEquals(RING, EquipSlots.target(RING, null, listOf(RING, RING_2)))
        assertEquals(RING_2, EquipSlots.target(RING, RING_2, listOf(RING, RING_2)))
        // Слот выбирают только для кольца: сапоги встают в сапоги, что бы ни просили
        assertEquals(BOOTS, EquipSlots.target(BOOTS, RING_2, emptyList()))
    }

    @Test
    fun a_two_handed_weapon_frees_both_hands() {
        assertEquals(setOf(WEAPON_1H, SHIELD, QUIVER), EquipSlots.displaced(WEAPON_2H, null, null))
    }

    @Test
    fun a_bow_goes_with_a_quiver_and_anything_else_with_a_shield() {
        assertEquals(setOf(WEAPON_2H, SHIELD), EquipSlots.displaced(WEAPON_1H, BOW, null))
        assertEquals(setOf(WEAPON_2H, QUIVER), EquipSlots.displaced(WEAPON_1H, SWORD, null))
        assertEquals(setOf(WEAPON_2H, QUIVER, WEAPON_1H), EquipSlots.displaced(SHIELD, null, BOW))
        assertEquals(setOf(WEAPON_2H, QUIVER), EquipSlots.displaced(SHIELD, null, SWORD))
        assertEquals(setOf(WEAPON_2H, SHIELD, WEAPON_1H), EquipSlots.displaced(QUIVER, null, SWORD))
        assertEquals(setOf(WEAPON_2H, SHIELD), EquipSlots.displaced(QUIVER, null, BOW))
        assertEquals(emptySet(), EquipSlots.displaced(BOOTS, null, BOW))
    }
}
