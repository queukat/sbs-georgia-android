package com.queukat.sbsgeorgia.ui.importstatement

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.data.importer.ImportedPdfDocument
import com.queukat.sbsgeorgia.data.importer.StatementDocumentReader
import com.queukat.sbsgeorgia.data.importer.StatementTextExtractor
import com.queukat.sbsgeorgia.domain.model.ApprovedImportedStatementRow
import com.queukat.sbsgeorgia.domain.model.ConfirmImportedStatementResult
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.ImportedStatementImportInfo
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.repository.FxRateRepository
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.StatementImportRepository
import com.queukat.sbsgeorgia.domain.service.tbc.TbcStatementParser
import com.queukat.sbsgeorgia.domain.usecase.ConfirmStatementImportUseCase
import com.queukat.sbsgeorgia.domain.usecase.DetectImportedTaxPaymentCandidatesUseCase
import com.queukat.sbsgeorgia.domain.usecase.LoadStatementImportPreviewUseCase
import com.queukat.sbsgeorgia.domain.usecase.ResolveFxForMonthsUseCase
import com.queukat.sbsgeorgia.domain.usecase.ResolveMonthFxUseCase
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportStatementViewModelTest {
    @Test
    fun taxOnlyPreviewCanBeConfirmedAndStoresExcludedTransactions() = runBlocking {
        val statementRepository = RecordingStatementImportRepository()
        val viewModel =
            ImportStatementViewModel(
                loadStatementImportPreviewUseCase =
                LoadStatementImportPreviewUseCase(
                    statementDocumentReader = TaxOnlyStatementDocumentReader(),
                    statementTextExtractor = TaxOnlyStatementTextExtractor(),
                    tbcStatementParser = TbcStatementParser(),
                    statementImportRepository = statementRepository
                ),
                confirmStatementImportUseCase =
                ConfirmStatementImportUseCase(
                    statementImportRepository = statementRepository,
                    resolveFxForMonthsUseCase = ResolveFxForMonthsUseCase(
                        incomeRepository = EmptyIncomeRepository(),
                        resolveMonthFxUseCase = ResolveMonthFxUseCase(
                            fxRateRepository = EmptyFxRateRepository(),
                            incomeRepository = EmptyIncomeRepository(),
                            clock = fixedClock
                        )
                    ),
                    detectImportedTaxPaymentCandidatesUseCase = DetectImportedTaxPaymentCandidatesUseCase(),
                    clock = fixedClock
                ),
                appContext = ApplicationProvider.getApplicationContext<Context>()
            )

        viewModel.loadDocument(Uri.parse("content://tax-only-statement"))

        val preview =
            withTimeout(5_000) {
                viewModel.uiState.first { state -> !state.isLoading && state.rows.size == 2 }
            }
        assertEquals(0, preview.selectedIncomeCount)
        assertEquals(2, preview.detectedTaxPaymentCount)
        assertTrue(preview.canImport)

        viewModel.importApprovedRows()

        val success =
            withTimeout(5_000) {
                viewModel.uiState.first { it.importSuccess != null }.importSuccess
            }
        assertNotNull(success)
        assertEquals(0, success?.importedIncomeCount)
        assertEquals(2, success?.storedTransactionCount)
        assertEquals(2, statementRepository.confirmedRows.size)
        assertTrue(statementRepository.confirmedRows.all { it.finalInclusion == DeclarationInclusion.EXCLUDED })
    }

    private class TaxOnlyStatementDocumentReader : StatementDocumentReader {
        override suspend fun read(uriString: String): ImportedPdfDocument = ImportedPdfDocument(
            fileName = "tax-only.pdf",
            sourceFingerprint = "tax-only-fingerprint",
            bytes = ByteArray(0)
        )
    }

    private class TaxOnlyStatementTextExtractor : StatementTextExtractor {
        override suspend fun extractText(documentBytes: ByteArray): String = statementText

        override suspend fun extractTextCandidates(documentBytes: ByteArray): List<String> = listOf(statementText)
    }

    private class RecordingStatementImportRepository : StatementImportRepository {
        val confirmedRows = mutableListOf<ApprovedImportedStatementRow>()

        override suspend fun hasStatementFingerprint(sourceFingerprint: String): Boolean = false

        override suspend fun getStatementImportInfo(sourceFingerprint: String): ImportedStatementImportInfo? = null

        override suspend fun hasTransactionFingerprint(transactionFingerprint: String): Boolean = false

        override suspend fun confirmImport(
            sourceFileName: String,
            sourceFingerprint: String,
            rows: List<ApprovedImportedStatementRow>,
            importedAtEpochMillis: Long
        ): ConfirmImportedStatementResult {
            confirmedRows += rows.filterNot(ApprovedImportedStatementRow::duplicate)
            return ConfirmImportedStatementResult(
                importedIncomeCount = 0,
                storedTransactionCount = confirmedRows.size,
                skippedDuplicateCount = 0,
                excludedCount = confirmedRows.size
            )
        }
    }

    private class EmptyIncomeRepository : IncomeRepository {
        override fun observeAll(): Flow<List<IncomeEntry>> = emptyFlow()

        override fun observeByMonth(yearMonth: YearMonth): Flow<List<IncomeEntry>> = emptyFlow()

        override suspend fun getById(id: Long): IncomeEntry? = null

        override suspend fun upsert(entry: IncomeEntry): Long = entry.id

        override suspend fun deleteById(id: Long) = Unit
    }

    private class EmptyFxRateRepository : FxRateRepository {
        override suspend fun getBestRate(rateDate: java.time.LocalDate, currencyCode: String) = null

        override suspend fun getRate(rateDate: java.time.LocalDate, currencyCode: String, manualOverride: Boolean) =
            null

        override suspend fun fetchOfficialRate(rateDate: java.time.LocalDate, currencyCode: String) =
            com.queukat.sbsgeorgia.domain.repository.FxRateFetchResult.NotFound

        override suspend fun upsertManualOverride(
            rateDate: java.time.LocalDate,
            currencyCode: String,
            units: Int,
            rateToGel: java.math.BigDecimal
        ) = throw UnsupportedOperationException()
    }

    private companion object {
        val fixedClock: Clock = Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC)

        val statementText =
            """
            Account Statement
            Statement currency: GEL
            Date  Description  Additional Information  Paid Out  Paid In  Balance
            02/04/2026  TREASURY SINGLE ACCOUNT  101001000  81.60 GEL  -  918.40 GEL
            03/04/2026  REVENUE SERVICE  Tax payment  75.00 GEL  -  843.40 GEL
            """.trimIndent()
    }
}
