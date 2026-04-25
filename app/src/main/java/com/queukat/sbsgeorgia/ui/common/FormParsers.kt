package com.queukat.sbsgeorgia.ui.common

import java.time.LocalDate

sealed interface DateParseResult {
    data object Empty : DateParseResult
    data class Valid(val date: LocalDate) : DateParseResult
    data class Invalid(val raw: String) : DateParseResult
}

object DateInputParser {
    fun parseOptionalIsoDate(value: String): DateParseResult {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return DateParseResult.Empty

        return runCatching { LocalDate.parse(trimmed) }
            .fold(
                onSuccess = DateParseResult::Valid,
                onFailure = { DateParseResult.Invalid(raw = value) },
            )
    }
}

fun DateParseResult.dateOrNull(): LocalDate? = when (this) {
    DateParseResult.Empty -> null
    is DateParseResult.Valid -> date
    is DateParseResult.Invalid -> null
}
