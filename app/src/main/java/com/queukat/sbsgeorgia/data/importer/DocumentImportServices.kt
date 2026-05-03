package com.queukat.sbsgeorgia.data.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.queukat.sbsgeorgia.di.IoDispatcher
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

data class ImportedPdfDocument(val fileName: String, val sourceFingerprint: String, val bytes: ByteArray)

interface StatementDocumentReader {
    suspend fun read(uriString: String): ImportedPdfDocument
}

interface StatementTextExtractor {
    suspend fun extractText(documentBytes: ByteArray): String

    suspend fun extractTextCandidates(documentBytes: ByteArray): List<String> = listOf(extractText(documentBytes))
}

@Singleton
class AndroidStatementDocumentReader
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : StatementDocumentReader {
    override suspend fun read(uriString: String): ImportedPdfDocument = withContext(ioDispatcher) {
        val uri = Uri.parse(uriString)
        val fileName = resolveDisplayName(uri) ?: "statement.pdf"
        val bytes =
            context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() }
                ?: error("Unable to open the selected PDF.")
        ImportedPdfDocument(
            fileName = fileName,
            sourceFingerprint = sha256(bytes),
            bytes = bytes
        )
    }

    private fun resolveDisplayName(uri: Uri): String? = context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            cursor.getString(0)
        }

    private fun sha256(bytes: ByteArray): String = MessageDigest
        .getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

@Singleton
class PdfBoxStatementTextExtractor
@Inject
constructor(
    private val pdfBoxInitializer: PdfBoxInitializer,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : StatementTextExtractor {
    override suspend fun extractText(documentBytes: ByteArray): String = extractTextCandidates(documentBytes).first()

    override suspend fun extractTextCandidates(documentBytes: ByteArray): List<String> = withContext(ioDispatcher) {
        pdfBoxInitializer.ensureInitialized()
        PDDocument.load(documentBytes).use { document ->
            listOf(false, true)
                .map { sortByPosition -> extractText(document, sortByPosition) }
                .distinct()
        }
    }

    private fun extractText(document: PDDocument, sortByPosition: Boolean): String = PDFTextStripper()
        .apply {
            this.sortByPosition = sortByPosition
        }.getText(document)
}
