package com.sperance.exileforge.rules

import com.sperance.exileforge.rules.content.Counter
import com.sperance.exileforge.rules.content.Quest
import com.sperance.exileforge.rules.content.QuestCondition
import com.sperance.exileforge.rules.content.QuestKind
import com.sperance.exileforge.rules.content.QuestLog
import com.sperance.exileforge.rules.content.QuestProgress
import com.sperance.exileforge.rules.content.Rarity
import kotlin.test.Test
import kotlin.test.assertEquals

/** Прогресс заданий: зона зачёта, обнуление условиями, потолок и вклад в гильдию. */
class QuestProgressTest {
    private fun quest(vararg conditions: QuestCondition) =
        Quest("q", QuestKind.DAILY, "KILL", Counter.KILLS, Rarity.RARE, 10, zones = listOf("Z1"), conditions = conditions.toList())

    @Test
    fun `kills count only in the quest zones and stop at the target`() {
        val log = QuestLog(daily = mutableListOf(quest()))
        QuestProgress.advance(log, Counter.KILLS, 4, "Z2", null, 0)
        QuestProgress.advance(log, Counter.KILLS, 4, "Z1", null, 0)
        QuestProgress.advance(log, Counter.KILLS, 40, "Z1", null, 0)
        assertEquals(10, log.daily[0].progress)
    }

    @Test
    fun `death and a new run reset quests with their conditions`() {
        val log = QuestLog(daily = mutableListOf(quest(QuestCondition.NO_DEATH), quest(QuestCondition.ONE_RUN)))
        QuestProgress.advance(log, Counter.KILLS, 5, "Z1", null, 0)
        QuestProgress.advance(log, Counter.DEATHS, 1, "Z1", null, 0)
        QuestProgress.advance(log, Counter.RUNS, 1, "Z1", null, 0)
        assertEquals(listOf(0L, 0L), log.daily.map { it.progress })
    }

    @Test
    fun `guild tallies start over with a new day`() {
        val log = QuestLog()
        QuestProgress.advance(log, Counter.KILLS, 3, null, "g", 0)
        QuestProgress.advance(log, Counter.KILLS, 2, null, "g", 86_400_000L)
        assertEquals(2, log.guild!!.dayCounts[Counter.KILLS])
        assertEquals(5, log.guild!!.weekCounts[Counter.KILLS])
    }
}
