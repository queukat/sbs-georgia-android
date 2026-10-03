package com.queukat.sbsgeorgia.data.importer

data class ImportedPdfDocument(val fileName: String, val sourceFingerprint: String, val bytes: ByteArray)

fun interface StatementDocumentReader {
    suspend fun read(uriString: String): ImportedPdfDocument
}

fun interface StatementTextExtractor {
    suspend fun extractText(documentBytes: ByteArray): String

    suspend fun extractTextCandidates(documentBytes: ByteArray): List<String> = listOf(extractText(documentBytes))
}
