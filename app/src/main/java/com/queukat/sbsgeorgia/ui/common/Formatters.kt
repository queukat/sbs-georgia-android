package com.queukat.sbsgeorgia.ui.common

import java.math.BigDecimal
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private fun currentLocale(): Locale = Locale.getDefault()

private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

fun YearMonth.formatMonthYear(): String = atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", currentLocale()))

fun LocalDate.formatIsoDate(): String = format(dateFormatter)

fun formatAmount(amount: BigDecimal, currencyCode: String): String {
    val formatter = NumberFormat.getCurrencyInstance(currentLocale())
    formatter.currency = Currency.getInstance(currencyCode.uppercase())
    return formatter.format(amount)
}
