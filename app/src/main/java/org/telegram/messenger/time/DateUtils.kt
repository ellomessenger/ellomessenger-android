package org.telegram.messenger.time

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class DateUtils {

    /**
     * @return Formatted string of the current week dates period. Examples: "Jun 5 - 11", "May 29 - Jun 4"
     */
    fun currentWeek():String {
        val calendar: Calendar = Calendar.getInstance()
        val dayToday: Int = calendar.get(Calendar.DAY_OF_WEEK)

        val today = LocalDate.now()
        val beginWeekDate = today.minusDays((dayToday - 2).toLong())
        val endWeekDate = beginWeekDate.plusDays(6)

        val beginDate = beginWeekDate.format(DateTimeFormatter.ofPattern("MMM d"))
        val endDate =
            endWeekDate.format(DateTimeFormatter.ofPattern(if (beginWeekDate.monthValue == endWeekDate.monthValue) "d" else "MMM d"))
        return "$beginDate - $endDate"
    }

    fun currentMonth():String = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM"))

    fun currentYear():String = LocalDate.now().format(DateTimeFormatter.ofPattern("Y"))

}