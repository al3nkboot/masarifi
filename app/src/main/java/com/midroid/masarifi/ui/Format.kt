package com.midroid.masarifi.ui

import com.midroid.masarifi.data.AppSettings
import com.midroid.masarifi.util.Money
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    val zone: ZoneId get() = ZoneId.systemDefault()
    fun locale(ar: Boolean): Locale = if (ar) Locale.forLanguageTag("ar") else Locale.ENGLISH

    fun money(milli: Long, s: AppSettings, withSymbol: Boolean = true) = Money.format(milli, s.currencyInfo, s.arabic, withSymbol)

    fun monthTitle(ym: YearMonth, ar: Boolean): String =
        ym.month.getDisplayName(if (ar) TextStyle.FULL_STANDALONE else TextStyle.SHORT, locale(ar)) + " " + ym.year

    fun monthShort(ym: YearMonth, ar: Boolean): String =
        ym.month.getDisplayName(if (ar) TextStyle.FULL_STANDALONE else TextStyle.SHORT, locale(ar))

    fun weekdayShort(d: DayOfWeek, ar: Boolean): String = d.getDisplayName(if (ar) TextStyle.FULL else TextStyle.SHORT, locale(ar))
    fun weekdayNarrow(d: DayOfWeek, ar: Boolean): String = d.getDisplayName(TextStyle.SHORT, locale(ar))

    fun toDateTime(millis: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    fun toDate(millis: Long): LocalDate = toDateTime(millis).toLocalDate()
    fun toMillis(dt: LocalDateTime): Long = dt.atZone(zone).toInstant().toEpochMilli()
    fun startOf(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun monthRange(ym: YearMonth): LongRange = startOf(ym.atDay(1)) until startOf(ym.plusMonths(1).atDay(1))

    fun dateTimeLabel(millis: Long, ar: Boolean): String {
        val dt = toDateTime(millis)
        val f = DateTimeFormatter.ofPattern("d/M/yyyy (EEE)   HH:mm", locale(ar))
        return dt.format(f)
    }

    fun dateLabel(millis: Long, ar: Boolean): String =
        toDateTime(millis).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale(ar)))

    fun time(millis: Long): String = toDateTime(millis).format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))

    fun percent(v: Double): String = String.format(Locale.US, "%.1f%%", v)
}
