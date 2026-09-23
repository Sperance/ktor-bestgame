package application.enums

/**
 * Влияние на предмете, как в POE.
 *
 * Влияние не меняет ни редкость, ни число аффиксов: оно открывает предмету
 * ещё один пул модификаторов, которых без него не бывает. Ставит его сфера
 * своего влияния, и одно влияние на предмет - второго не наложить.
 */
enum class EnumInfluence {

    /**
     * Влияние Создателя (Shaper).
     */
    SHAPER,

    /**
     * Влияние Древнего (Elder).
     */
    ELDER;

    companion object {
        /**
         * Сфера, которая ставит это влияние.
         */
        fun byOrb(orb: EnumCurrencyOrb): EnumInfluence? = when (orb) {
            EnumCurrencyOrb.SHAPERS_ORB -> SHAPER
            EnumCurrencyOrb.ELDER_ORB -> ELDER
            else -> null
        }
    }
}
