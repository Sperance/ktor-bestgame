package features.poe

import kotlinx.serialization.Serializable

@Serializable
enum class PoeRarity { NORMAL, MAGIC, RARE, UNIQUE }

@Serializable
data class PoeRoll(val id: String, val values: List<Int>, val fractured: Boolean = false)

/** Persisted state of ONE item, never of the shared equipment template. */
@Serializable
data class PoeItem(
    val baseId: String,
    val itemLevel: Int,
    val rarity: PoeRarity = PoeRarity.NORMAL,
    val implicits: List<PoeRoll> = emptyList(),
    val explicits: List<PoeRoll> = emptyList(),
    val quality: Int = 0,
    val corrupted: Boolean = false,
    val mirrored: Boolean = false,
    val influences: Set<String> = emptySet(),
    val split: Boolean = false,
    val catalogVersion: String = "3.29.3.3"
)

@Serializable
enum class PoeCurrency(val displayName: String) {
    TRANSMUTATION("Orb of Transmutation"), AUGMENTATION("Orb of Augmentation"), ALTERATION("Orb of Alteration"),
    ALCHEMY("Orb of Alchemy"), CHAOS("Chaos Orb"), REGAL("Regal Orb"), EXALTED("Exalted Orb"),
    SCOURING("Orb of Scouring"), ANNULMENT("Orb of Annulment"), DIVINE("Divine Orb"), BLESSED("Blessed Orb"),
    FRACTURING("Fracturing Orb"), MIRROR("Mirror of Kalandra")
}
