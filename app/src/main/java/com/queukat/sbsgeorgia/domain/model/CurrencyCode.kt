package com.queukat.sbsgeorgia.domain.model

import java.util.Locale

private val CurrencyCodeRegex = Regex("^[A-Z]{3}$")

fun normalizeCurrencyCode(value: String): String = value.trim().uppercase(Locale.ROOT)

fun isIsoLikeCurrencyCode(value: String): Boolean =
    normalizeCurrencyCode(value).matches(CurrencyCodeRegex)
