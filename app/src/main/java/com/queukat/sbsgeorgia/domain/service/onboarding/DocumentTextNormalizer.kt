package com.queukat.sbsgeorgia.domain.service

internal fun String.normalizedLines(): List<String> = lineSequence()
    .map { line -> line.replace(Regex("\\s+"), " ").trim() }
    .filter { it.isNotBlank() }
    .toList()

internal fun String.normalizedText(): String = normalizedLines().joinToString("\n").lowercase()
