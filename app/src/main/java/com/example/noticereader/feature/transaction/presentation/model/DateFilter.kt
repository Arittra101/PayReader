package com.example.noticereader.feature.transaction.presentation.model

import com.example.noticereader.feature.transaction.domain.BkashTime
import java.time.LocalDate

/**
 * The shared date filter above the list (§10.4). One filter applies to all three tabs at once.
 * Rows are matched on their payment time (occurred_at, falling back to received_at for a
 * parse-error row that has no payment time), never on received_at alone (§10.4).
 *
 * Default on app open is [Today] (§10.4).
 */
sealed interface DateFilter {

    /** The inclusive lower bound in epoch millis; rows at or after this pass. */
    fun startMillis(nowMillis: Long): Long

    /** The exclusive upper bound in epoch millis, or [Long.MAX_VALUE] for "up to now". */
    fun endMillisExclusive(nowMillis: Long): Long = Long.MAX_VALUE

    fun matches(timestampMillis: Long, nowMillis: Long): Boolean =
        timestampMillis >= startMillis(nowMillis) && timestampMillis < endMillisExclusive(nowMillis)

    data object Today : DateFilter {
        override fun startMillis(nowMillis: Long): Long = startOfTodayDhaka()
    }

    data object Last7Days : DateFilter {
        override fun startMillis(nowMillis: Long): Long = nowMillis - 7L * DAY_MILLIS
    }

    data object Last30Days : DateFilter {
        override fun startMillis(nowMillis: Long): Long = nowMillis - 30L * DAY_MILLIS
    }

    data object All : DateFilter {
        override fun startMillis(nowMillis: Long): Long = Long.MIN_VALUE
    }

    /** A custom [start, end) range chosen from the date range picker (§10.4). */
    data class Custom(val start: Long, val endExclusive: Long) : DateFilter {
        override fun startMillis(nowMillis: Long): Long = start
        override fun endMillisExclusive(nowMillis: Long): Long = endExclusive
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** Midnight today in Asia/Dhaka, as epoch millis. */
        private fun startOfTodayDhaka(): Long =
            LocalDate.now(BkashTime.OFFSET).atStartOfDay(BkashTime.OFFSET).toInstant().toEpochMilli()
    }
}
