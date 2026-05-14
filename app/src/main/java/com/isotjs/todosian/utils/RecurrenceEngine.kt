package com.isotjs.todosian.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Supported rule patterns (case-insensitive):
 *   every day
 *   every weekday
 *   every weekend
 *   every N days            (N >= 1)
 *   every week
 *   every N weeks           (N >= 1)
 *   every week on <day>[, <day>...]
 *   every N weeks on <day>[, <day>...]
 *   every month
 *   every N months
 *   every month on the <Nth>
 *   every month on the last <day>
 *   every month on the last
 *   every year
 *   every N years
 *   Any of the above with " when done" appended
 */

internal object RecurrenceEngine {

    private val WHEN_DONE_SUFFIX = Regex("""\s+when\s+done\s*$""", RegexOption.IGNORE_CASE)

    private val EVERY_N_DAYS = Regex("""^every\s+(\d+)\s+days?$""", RegexOption.IGNORE_CASE)
    private val EVERY_DAY = Regex("""^every\s+day$""", RegexOption.IGNORE_CASE)
    private val EVERY_WEEKDAY = Regex("""^every\s+weekday$""", RegexOption.IGNORE_CASE)
    private val EVERY_WEEKEND = Regex("""^every\s+weekend$""", RegexOption.IGNORE_CASE)

    private val EVERY_N_WEEKS = Regex("""^every\s+(\d+)\s+weeks?$""", RegexOption.IGNORE_CASE)
    private val EVERY_WEEK = Regex("""^every\s+week$""", RegexOption.IGNORE_CASE)
    private val EVERY_WEEK_ON = Regex(
        """^every\s+(?:(\d+)\s+)?weeks?\s+on\s+(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    private val EVERY_N_MONTHS = Regex("""^every\s+(\d+)\s+months?$""", RegexOption.IGNORE_CASE)
    private val EVERY_MONTH = Regex("""^every\s+month$""", RegexOption.IGNORE_CASE)
    private val EVERY_MONTH_ON_THE_NTH = Regex(
        """^every\s+month\s+on\s+the\s+(\d+)(?:st|nd|rd|th)$""",
        RegexOption.IGNORE_CASE,
    )
    private val EVERY_MONTH_ON_LAST = Regex(
        """^every\s+month\s+on\s+the\s+last(?:\s+(\w+))?$""",
        RegexOption.IGNORE_CASE,
    )

    private val EVERY_N_YEARS = Regex("""^every\s+(\d+)\s+years?$""", RegexOption.IGNORE_CASE)
    private val EVERY_YEAR = Regex("""^every\s+year$""", RegexOption.IGNORE_CASE)

    private val DAY_NAMES = mapOf(
        "monday" to DayOfWeek.MONDAY,
        "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY,
        "tue" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY,
        "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "thu" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY,
        "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY,
        "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        "sun" to DayOfWeek.SUNDAY,
    )

    data class NextOccurrence(
        val dueDate: LocalDate?,
        val scheduledDate: LocalDate?,
        val startDate: LocalDate?,
    )

    fun nextOccurrence(
        ruleText: String,
        dueDate: String?,
        scheduledDate: String?,
        startDate: String?,
        today: LocalDate = LocalDate.now(),
    ): NextOccurrence? {
        val stripped = WHEN_DONE_SUFFIX.replace(ruleText.trim(), "").trim()
        val whenDone = WHEN_DONE_SUFFIX.containsMatchIn(ruleText.trim())

        val dueParsed = dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val scheduledParsed = scheduledDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val startParsed = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val referenceDate: LocalDate? = dueParsed ?: scheduledParsed ?: startParsed

        val baseDate: LocalDate = if (whenDone || referenceDate == null) today else referenceDate

        val nextReference = computeNextReference(stripped, baseDate) ?: return null

        val nextDue = advanceDate(dueParsed, referenceDate, nextReference)
        val nextScheduled = advanceDate(scheduledParsed, referenceDate, nextReference)
        val nextStart = advanceDate(startParsed, referenceDate, nextReference)

        return NextOccurrence(
            dueDate = nextDue,
            scheduledDate = nextScheduled,
            startDate = nextStart,
        )
    }

    private fun computeNextReference(rule: String, base: LocalDate): LocalDate? {
        EVERY_DAY.find(rule)?.let { return base.plusDays(1) }
        EVERY_N_DAYS.find(rule)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return null
            return base.plusDays(n)
        }
        EVERY_WEEKDAY.find(rule)?.let { return nextWeekday(base) }
        EVERY_WEEKEND.find(rule)?.let { return nextWeekend(base) }

        EVERY_WEEK.find(rule)?.let { return base.plusWeeks(1) }
        EVERY_N_WEEKS.find(rule)?.let { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return null
            return base.plusWeeks(n)
        }
        EVERY_WEEK_ON.find(rule)?.let { m ->
            val weeks = m.groupValues[1].toLongOrNull() ?: 1L
            val dayNames = m.groupValues[2].split(",").map { it.trim().lowercase() }
            val days = dayNames.mapNotNull { DAY_NAMES[it] }.sorted()
            if (days.isEmpty()) return null
            return nextWeeklyOccurrence(base, weeks, days)
        }

        EVERY_MONTH.find(rule)?.let { return nextMonthSameDay(base, 1) }
        EVERY_N_MONTHS.find(rule)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return null
            return nextMonthSameDay(base, n)
        }
        EVERY_MONTH_ON_THE_NTH.find(rule)?.let { m ->
            val nth = m.groupValues[1].toIntOrNull() ?: return null
            return nextMonthOnNth(base, 1, nth)
        }
        EVERY_MONTH_ON_LAST.find(rule)?.let { m ->
            val dayName = m.groupValues[1].trim().lowercase()
            return if (dayName.isEmpty()) {
                nextMonthOnLast(base, 1)
            } else {
                val dow = DAY_NAMES[dayName] ?: return null
                nextMonthOnLastDow(base, 1, dow)
            }
        }

        EVERY_YEAR.find(rule)?.let { return nextYearSameDay(base, 1) }
        EVERY_N_YEARS.find(rule)?.let { m ->
            val n = m.groupValues[1].toIntOrNull() ?: return null
            return nextYearSameDay(base, n)
        }

        return null
    }

    // Date Helpers

    private fun advanceDate(
        date: LocalDate?,
        originalRef: LocalDate?,
        nextRef: LocalDate,
    ): LocalDate? {
        if (date == null || originalRef == null) return null
        val offsetDays = java.time.temporal.ChronoUnit.DAYS.between(originalRef, date)
        return nextRef.plusDays(offsetDays)
    }

    private fun nextWeekday(base: LocalDate): LocalDate {
        var next = base.plusDays(1)
        while (next.dayOfWeek == DayOfWeek.SATURDAY || next.dayOfWeek == DayOfWeek.SUNDAY) {
            next = next.plusDays(1)
        }
        return next
    }

    private fun nextWeekend(base: LocalDate): LocalDate {
        var next = base.plusDays(1)
        while (next.dayOfWeek != DayOfWeek.SATURDAY && next.dayOfWeek != DayOfWeek.SUNDAY) {
            next = next.plusDays(1)
        }
        return next
    }

    private fun nextWeeklyOccurrence(
        base: LocalDate,
        weeks: Long,
        days: List<DayOfWeek>,
    ): LocalDate {
        val periodStart = base.plusWeeks(weeks).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return days.map { dow ->
            periodStart.with(TemporalAdjusters.nextOrSame(dow))
        }.minBy { it.toEpochDay() }
    }

    private fun nextMonthSameDay(base: LocalDate, n: Int): LocalDate {
        return base.plusMonths(n.toLong()).let { candidate ->
            val max = candidate.month.length(candidate.isLeapYear)
            if (base.dayOfMonth > max) candidate.withDayOfMonth(max) else candidate
        }
    }

    private fun nextMonthOnNth(base: LocalDate, n: Int, nth: Int): LocalDate {
        val target = base.plusMonths(n.toLong())
        val max = target.month.length(target.isLeapYear)
        return target.withDayOfMonth(nth.coerceAtMost(max))
    }

    private fun nextMonthOnLast(base: LocalDate, n: Int): LocalDate {
        val target = base.plusMonths(n.toLong())
        return target.with(TemporalAdjusters.lastDayOfMonth())
    }

    private fun nextMonthOnLastDow(base: LocalDate, n: Int, dow: DayOfWeek): LocalDate {
        val target = base.plusMonths(n.toLong())
        return target.with(TemporalAdjusters.lastInMonth(dow))
    }

    private fun nextYearSameDay(base: LocalDate, n: Int): LocalDate {
        return runCatching { base.plusYears(n.toLong()) }.getOrElse {
            base.withDayOfMonth(28).plusYears(n.toLong())
        }
    }
}
