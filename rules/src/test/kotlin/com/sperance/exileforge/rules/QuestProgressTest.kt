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

/** Прогресс заданий: обнуление условиями, потолок и вклад в гильдию. */
class QuestProgressTest {
    private fun quest(vararg conditions: QuestCondition) = Quest("q", QuestKind.DAILY, "KILL", Counter.KILLS, Rarity.RARE, 10, conditions = conditions.toList())

    @Test
    fun `kills count anywhere and stop at the target`() {
        val log = QuestLog(daily = mutableListOf(quest()))
        QuestProgress.advance(log, Counter.KILLS, 4, null, 0)
        QuestProgress.advance(log, Counter.KILLS, 40, null, 0)
        assertEquals(10, log.daily[0].progress)
    }

    @Test
    fun `death and a new run reset quests with their conditions`() {
        val log = QuestLog(daily = mutableListOf(quest(QuestCondition.NO_DEATH), quest(QuestCondition.ONE_RUN)))
        QuestProgress.advance(log, Counter.KILLS, 5, null, 0)
        QuestProgress.advance(log, Counter.DEATHS, 1, null, 0)
        QuestProgress.advance(log, Counter.RUNS, 1, null, 0)
        assertEquals(listOf(0L, 0L), log.daily.map { it.progress })
    }

    @Test
    fun `guild tallies start over with a new day`() {
        val log = QuestLog()
        QuestProgress.advance(log, Counter.KILLS, 3, "g", 0)
        QuestProgress.advance(log, Counter.KILLS, 2, "g", 86_400_000L)
        assertEquals(2, log.guild!!.dayCounts[Counter.KILLS])
        assertEquals(5, log.guild!!.weekCounts[Counter.KILLS])
    }
}
