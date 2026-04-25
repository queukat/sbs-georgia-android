package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.data.importer.ImportedPdfDocument
import com.queukat.sbsgeorgia.data.importer.StatementDocumentReader
import com.queukat.sbsgeorgia.data.importer.StatementTextExtractor
import com.queukat.sbsgeorgia.domain.model.ApprovedImportedStatementRow
import com.queukat.sbsgeorgia.domain.model.ConfirmImportedStatementResult
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRate
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.ImportedStatementImportInfo
import com.queukat.sbsgeorgia.domain.model.StatementMoney
import com.queukat.sbsgeorgia.domain.repository.FxRateFetchResult
import com.queukat.sbsgeorgia.domain.repository.FxRateRepository
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.StatementImportRepository
import com.queukat.sbsgeorgia.domain.service.TbcStatementParser
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

class StatementImportUseCasesTest {
    private val fixedClock: Clock = Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun loadPreviewKeepsPreviewAvailableWhenStatementFingerprintAlreadyExists() = kotlinx.coroutines.test.runTest {
        val repository = FakeStatementImportRepository(
            existingStatementFingerprints = mutableSetOf("known-statement"),
            existingStatementImports = mutableMapOf(
                "known-statement" to ImportedStatementImportInfo(
                    sourceFileName = "statement.pdf",
                    sourceFingerprint = "known-statement",
                    importedAtEpochMillis = 1_775_126_400_000L,
                ),
            ),
        )

        val result = LoadStatementImportPreviewUseCase(
            statementDocumentReader = FakeStatementDocumentReader(
                ImportedPdfDocument(
                    fileName = "statement.pdf",
                    sourceFingerprint = "known-statement",
                    bytes = ByteArray(0),
                ),
            ),
            statementTextExtractor = FakeStatementTextExtractor(fixtureText("tbc_statement_v1_extracted.txt")),
            tbcStatementParser = TbcStatementParser(),
            statementImportRepository = repository,
        ).invoke("content://statement")

        assertTrue(result.alreadyImported)
        assertEquals("statement.pdf", result.existingImport?.sourceFileName)
        assertEquals(4, requireNotNull(result.preview).rows.size)
    }

    @Test
    fun loadPreviewMarksDuplicateTransactionsFromRepository() = kotlinx.coroutines.test.runTest {
        val duplicateFingerprint = TbcStatementParser()
            .parse("statement.pdf", "fingerprint", fixtureText("tbc_statement_v1_extracted.txt"))
            .rows
            .first()
            .transactionFingerprint
        val repository = FakeStatementImportRepository(
            existingTransactionFingerprints = mutableSetOf(duplicateFingerprint),
        )

        val result = LoadStatementImportPreviewUseCase(
            statementDocumentReader = FakeStatementDocumentReader(
                ImportedPdfDocument(
                    fileName = "statement.pdf",
                    sourceFingerprint = "new-statement",
                    bytes = ByteArray(0),
                ),
            ),
            statementTextExtractor = FakeStatementTextExtractor(fixtureText("tbc_statement_v1_extracted.txt")),
            tbcStatementParser = TbcStatementParser(),
            statementImportRepository = repository,
        ).invoke("content://statement")

        val firstRow = requireNotNull(result.preview).rows.first()
        assertTrue(firstRow.duplicate)
    }

    @Test
    fun loadPreviewMarksLaterInternalFingerprintCollisionsAsDuplicates() = kotlinx.coroutines.test.runTest {
        val result = LoadStatementImportPreviewUseCase(
            statementDocumentReader = FakeStatementDocumentReader(
                ImportedPdfDocument(
                    fileName = "statement.pdf",
                    sourceFingerprint = "new-statement",
                    bytes = ByteArray(0),
                ),
            ),
            statementTextExtractor = FakeStatementTextExtractor(
                """
                    Account Statement
                    Statement currency: USD
                    Date  Description  Additional Information  Paid Out  Paid In  Balance
                    15/03/2026  FOR SOFTWARE SERVICES  Invoice 001  0.00 USD  125.50 USD  1240.75 USD
                    15/03/2026  FOR SOFTWARE SERVICES  Invoice 001  0.00 USD  125.50 USD  1240.75 USD
                """.trimIndent(),
            ),
            tbcStatementParser = TbcStatementParser(),
            statementImportRepository = FakeStatementImportRepository(),
        ).invoke("content://statement")

        val rows = requireNotNull(result.preview).rows
        assertEquals(2, rows.size)
        assertTrue(rows.first().duplicate.not())
        assertTrue(rows.last().duplicate)
    }

    @Test
    fun confirmImportPreservesManualCorrectionsAndCountsDuplicates() = kotlinx.coroutines.test.runTest {
        val repository = FakeStatementImportRepository()
        val fxIncomeRepository = StatementImportFakeIncomeRepository()

        val result = ConfirmStatementImportUseCase(
            statementImportRepository = repository,
            resolveFxForMonthsUseCase = resolveFxForMonthsUseCase(fxIncomeRepository),
            applyImportedTaxPaymentsUseCase = ApplyImportedTaxPaymentsUseCase(),
            clock = fixedClock,
        ).invoke(
            sourceFileName = "statement.pdf",
            sourceFingerprint = "new-statement",
            rows = listOf(
                ApprovedImportedStatementRow(
                    transactionFingerprint = "tx-1",
                    incomeDate = LocalDate.of(2026, 3, 15),
                    description = "FOR SOFTWARE SERVICES",
                    additionalInformation = "Invoice 001",
                    paidOut = StatementMoney(BigDecimal("0.00"), "USD"),
                    paidIn = StatementMoney(BigDecimal("125.50"), "USD"),
                    balance = StatementMoney(BigDecimal("1240.75"), "USD"),
                    suggestedInclusion = DeclarationInclusion.INCLUDED,
                    finalInclusion = DeclarationInclusion.INCLUDED,
                    amount = BigDecimal("130.00"),
                    currency = "EUR",
                    sourceCategory = "Edited category",
                    duplicate = false,
                ),
                ApprovedImportedStatementRow(
                    transactionFingerprint = "tx-2",
                    incomeDate = LocalDate.of(2026, 3, 14),
                    description = "INTERNAL TRANSFER",
                    additionalInformation = "Own account transfer",
                    paidOut = StatementMoney(BigDecimal("500.00"), "USD"),
                    paidIn = StatementMoney(BigDecimal("0.00"), "USD"),
                    balance = StatementMoney(BigDecimal("740.75"), "USD"),
                    suggestedInclusion = DeclarationInclusion.EXCLUDED,
                    finalInclusion = DeclarationInclusion.EXCLUDED,
                    amount = BigDecimal("500.00"),
                    currency = "USD",
                    sourceCategory = "Own account transfer",
                    duplicate = true,
                ),
            ),
        )

        assertEquals(1, result.importResult.importedIncomeCount)
        assertEquals(1, result.importResult.skippedDuplicateCount)
        assertEquals(0, result.autoResolvedFxEntryCount)
        assertEquals(1, repository.confirmedRows.size)
        assertEquals("EUR", repository.confirmedRows.single().currency)
        assertEquals("Edited category", repository.confirmedRows.single().sourceCategory)
    }

    @Test
    fun confirmImportAutomaticallyResolvesFxForAffectedMonths() = kotlinx.coroutines.test.runTest {
        val fxIncomeRepository = StatementImportFakeIncomeRepository()
        val repository = FakeStatementImportRepository(
            onConfirm = { rows, importedAtEpochMillis ->
                rows.filterNot { it.duplicate || it.finalInclusion != DeclarationInclusion.INCLUDED }
                    .forEachIndexed { index, row ->
                        fxIncomeRepository.upsert(
                            IncomeEntry(
                                id = index + 1L,
                                sourceType = IncomeSourceType.IMPORTED_STATEMENT,
                                incomeDate = requireNotNull(row.incomeDate),
                                originalAmount = row.amount,
                                originalCurrency = row.currency,
                                sourceCategory = row.sourceCategory,
                                note = row.additionalInformation.orEmpty(),
                                declarationInclusion = row.finalInclusion,
                                gelEquivalent = null,
                                rateSource = FxRateSource.NONE,
                                manualFxOverride = false,
                                createdAtEpochMillis = importedAtEpochMillis,
                                updatedAtEpochMillis = importedAtEpochMillis,
                            ),
                        )
                    }
            },
        )
        val fxRateRepository = StatementImportFakeFxRateRepository(
            cachedRates = mapOf(
                (LocalDate.of(2026, 3, 15) to "USD") to FxRate(
                    rateDate = LocalDate.of(2026, 3, 15),
                    currencyCode = "USD",
                    units = 1,
                    rateToGel = BigDecimal("2.70"),
                    source = FxRateSource.OFFICIAL_NBG_JSON,
                    manualOverride = false,
                ),
            ),
        )

        val result = ConfirmStatementImportUseCase(
            statementImportRepository = repository,
            resolveFxForMonthsUseCase = resolveFxForMonthsUseCase(fxIncomeRepository, fxRateRepository),
            applyImportedTaxPaymentsUseCase = ApplyImportedTaxPaymentsUseCase(),
            clock = fixedClock,
        ).invoke(
            sourceFileName = "statement.pdf",
            sourceFingerprint = "new-statement",
            rows = listOf(
                ApprovedImportedStatementRow(
                    transactionFingerprint = "tx-1",
                    incomeDate = LocalDate.of(2026, 3, 15),
                    description = "FOR SOFTWARE SERVICES",
                    additionalInformation = "Invoice 001",
                    paidOut = null,
                    paidIn = StatementMoney(BigDecimal("125.50"), "USD"),
                    balance = StatementMoney(BigDecimal("1240.75"), "USD"),
                    suggestedInclusion = DeclarationInclusion.INCLUDED,
                    finalInclusion = DeclarationInclusion.INCLUDED,
                    amount = BigDecimal("125.50"),
                    currency = "USD",
                    sourceCategory = "Software services",
                    duplicate = false,
                ),
            ),
        )

        assertEquals(1, result.autoResolvedFxEntryCount)
        assertEquals(0, result.remainingUnresolvedFxEntryCount)
        assertEquals(BigDecimal("338.85"), fxIncomeRepository.observeAll().first().single().gelEquivalent)
    }

    @Test
    fun confirmImportFlagsTreasuryTaxPaymentForManualReview() = kotlinx.coroutines.test.runTest {
        val result = ConfirmStatementImportUseCase(
            statementImportRepository = FakeStatementImportRepository(),
            resolveFxForMonthsUseCase = resolveFxForMonthsUseCase(StatementImportFakeIncomeRepository()),
            applyImportedTaxPaymentsUseCase = ApplyImportedTaxPaymentsUseCase(),
            clock = fixedClock,
        ).invoke(
            sourceFileName = "statement.pdf",
            sourceFingerprint = "new-statement",
            rows = listOf(
                ApprovedImportedStatementRow(
                    transactionFingerprint = "tax-tx-1",
                    incomeDate = LocalDate.of(2026, 4, 2),
                    description = "გადასახადების ერთიანი კოდი ხაზინის ერთიანი ანგარიში. საგადასახადო ინსპექცია",
                    additionalInformation = "(გადასახადები), TRESGE22, 101001000",
                    paidOut = StatementMoney(BigDecimal("81.60"), "GEL"),
                    paidIn = null,
                    balance = StatementMoney(BigDecimal("674.99"), "GEL"),
                    suggestedInclusion = DeclarationInclusion.EXCLUDED,
                    finalInclusion = DeclarationInclusion.EXCLUDED,
                    amount = BigDecimal("81.60"),
                    currency = "GEL",
                    sourceCategory = "Tax payment",
                    duplicate = false,
                ),
            ),
        )

        assertEquals(0, result.appliedTaxPaymentCount)
        assertEquals(1, result.reviewRequiredTaxPaymentCount)
    }

    @Test
    fun confirmImportRejectsIncludedRowWithoutDate() = kotlinx.coroutines.test.runTest {
        var error: IllegalArgumentException? = null

        try {
            ConfirmStatementImportUseCase(
                statementImportRepository = FakeStatementImportRepository(),
                resolveFxForMonthsUseCase = resolveFxForMonthsUseCase(StatementImportFakeIncomeRepository()),
                applyImportedTaxPaymentsUseCase = ApplyImportedTaxPaymentsUseCase(),
                clock = fixedClock,
            ).invoke(
                sourceFileName = "statement.pdf",
                sourceFingerprint = "new-statement",
                rows = listOf(
                    ApprovedImportedStatementRow(
                        transactionFingerprint = "tx-missing-date",
                        incomeDate = null,
                        description = "FOR SOFTWARE SERVICES",
                        additionalInformation = "Invoice 404",
                        paidOut = null,
                        paidIn = StatementMoney(BigDecimal("125.50"), "USD"),
                        balance = StatementMoney(BigDecimal("1240.75"), "USD"),
                        suggestedInclusion = DeclarationInclusion.INCLUDED,
                        finalInclusion = DeclarationInclusion.INCLUDED,
                        amount = BigDecimal("125.50"),
                        currency = "USD",
                        sourceCategory = "Software services",
                        duplicate = false,
                    ),
                ),
            )
        } catch (caught: IllegalArgumentException) {
            error = caught
        }

        assertTrue(error?.message.orEmpty().contains("income date"))
    }

    @Test
    fun loadPreviewHandlesOverlappingStatementWindowByFlaggingOnlySharedRows() = kotlinx.coroutines.test.runTest {
        val alreadyImportedFingerprint = TbcStatementParser()
            .parse("statement.pdf", "fingerprint", fixtureText("tbc_statement_v1_extracted.txt"))
            .rows
            .first { it.description == "FOR SOFTWARE SERVICES" }
            .transactionFingerprint
        val repository = FakeStatementImportRepository(
            existingTransactionFingerprints = mutableSetOf(alreadyImportedFingerprint),
        )

        val result = LoadStatementImportPreviewUseCase(
            statementDocumentReader = FakeStatementDocumentReader(
                ImportedPdfDocument(
                    fileName = "overlap.pdf",
                    sourceFingerprint = "overlap-statement",
                    bytes = ByteArray(0),
                ),
            ),
            statementTextExtractor = FakeStatementTextExtractor(
                fixtureText("tbc_statement_v1_overlap_extracted.txt"),
            ),
            tbcStatementParser = TbcStatementParser(),
            statementImportRepository = repository,
        ).invoke("content://overlap")

        val previewRows = requireNotNull(result.preview).rows
        assertEquals(2, previewRows.size)
        assertTrue(previewRows.first { it.description == "FOR SOFTWARE SERVICES" && it.additionalInformation == "Invoice 001" }.duplicate)
        assertTrue(previewRows.first { it.additionalInformation == "Invoice 002" }.duplicate.not())
    }

    @Test
    fun loadPreviewSupportsWrappedTbcFixtureWithoutTreatingItAsUnsupported() = kotlinx.coroutines.test.runTest {
        val result = LoadStatementImportPreviewUseCase(
            statementDocumentReader = FakeStatementDocumentReader(
                ImportedPdfDocument(
                    fileName = "wrapped.pdf",
                    sourceFingerprint = "wrapped-statement",
                    bytes = ByteArray(0),
                ),
            ),
            statementTextExtractor = FakeStatementTextExtractor(
                fixtureText("tbc_statement_v1_wrapped_extracted.txt"),
            ),
            tbcStatementParser = TbcStatementParser(),
            statementImportRepository = FakeStatementImportRepository(),
        ).invoke("content://wrapped")

        val preview = requireNotNull(result.preview)
        assertEquals(2, preview.rows.size)
        assertEquals(0, preview.skippedLineCount)
    }

    @Test
    fun loadPreviewFallsBackToAlternativeExtractionWhenPrimaryTextLooksBroken() = kotlinx.coroutines.test.runTest {
        val result = LoadStatementImportPreviewUseCase(
            statementDocumentReader = FakeStatementDocumentReader(
                ImportedPdfDocument(
                    fileName = "statement.pdf",
                    sourceFingerprint = "fallback-statement",
                    bytes = ByteArray(0),
                ),
            ),
            statementTextExtractor = FakeStatementTextExtractor(
                text = "unsupported extraction",
                candidates = listOf(
                    "unsupported extraction",
                    fixtureText("tbc_statement_v1_extracted.txt"),
                ),
            ),
            tbcStatementParser = TbcStatementParser(),
            statementImportRepository = FakeStatementImportRepository(),
        ).invoke("content://statement")

        val preview = requireNotNull(result.preview)
        assertEquals(4, preview.rows.size)
        assertEquals(0, preview.skippedLineCount)
    }

    @Test
    fun loadPreviewPrefersCandidateWithoutEmbeddedTransactionDateArtifacts() = kotlinx.coroutines.test.runTest {
        val badCandidate = """
            Account Statement
            Statement currency: USD
            Date  Description  Additional Information  Paid Out  Paid In  Balance
            12/01/2026  FOR SOFTWARE SERVICES 29/01/2026 WAVEACCESS USA  0.00 USD  3031.00 USD  3031.00 USD
            29/01/2026  FOR SOFTWARE SERVICES  WAVEACCESS USA  0.00 USD  3031.00 USD  6062.00 USD
        """.trimIndent()
        val goodCandidate = """
            Account Statement
            Statement currency: USD
            Date  Description  Additional Information  Paid Out  Paid In  Balance
            12/01/2026  FOR SOFTWARE SERVICES  WAVEACCESS USA  0.00 USD  3031.00 USD  3031.00 USD
            29/01/2026  FOR SOFTWARE SERVICES  WAVEACCESS USA  0.00 USD  3031.00 USD  6062.00 USD
        """.trimIndent()

        val result = LoadStatementImportPreviewUseCase(
            statementDocumentReader = FakeStatementDocumentReader(
                ImportedPdfDocument(
                    fileName = "statement.pdf",
                    sourceFingerprint = "candidate-selection",
                    bytes = ByteArray(0),
                ),
            ),
            statementTextExtractor = FakeStatementTextExtractor(
                text = badCandidate,
                candidates = listOf(badCandidate, goodCandidate),
            ),
            tbcStatementParser = TbcStatementParser(),
            statementImportRepository = FakeStatementImportRepository(),
        ).invoke("content://statement")

        val preview = requireNotNull(result.preview)
        assertEquals(2, preview.rows.size)
        assertTrue(preview.rows.none { row -> row.description.contains("29/01/2026") })
        assertTrue(preview.rows.none { row -> row.duplicate })
    }

    private fun fixtureText(fileName: String): String =
        requireNotNull(javaClass.getResource("/fixtures/$fileName"))
            .readText()

    private fun resolveFxForMonthsUseCase(
        incomeRepository: StatementImportFakeIncomeRepository,
        fxRateRepository: FxRateRepository = StatementImportFakeFxRateRepository(),
    ): ResolveFxForMonthsUseCase = ResolveFxForMonthsUseCase(
        incomeRepository = incomeRepository,
        resolveMonthFxUseCase = ResolveMonthFxUseCase(
            fxRateRepository = fxRateRepository,
            incomeRepository = incomeRepository,
            clock = fixedClock,
        ),
    )
}

private class FakeStatementDocumentReader(
    private val document: ImportedPdfDocument,
) : StatementDocumentReader {
    override suspend fun read(uriString: String): ImportedPdfDocument = document
}

private class FakeStatementTextExtractor(
    private val text: String,
    private val candidates: List<String> = listOf(text),
) : StatementTextExtractor {
    override suspend fun extractText(documentBytes: ByteArray): String = text

    override suspend fun extractTextCandidates(documentBytes: ByteArray): List<String> = candidates
}

private class FakeStatementImportRepository(
    val existingStatementFingerprints: MutableSet<String> = mutableSetOf(),
    val existingStatementImports: MutableMap<String, ImportedStatementImportInfo> = mutableMapOf(),
    val existingTransactionFingerprints: MutableSet<String> = mutableSetOf(),
    val onConfirm: (suspend (List<ApprovedImportedStatementRow>, Long) -> Unit)? = null,
) : StatementImportRepository {
    val confirmedRows = mutableListOf<ApprovedImportedStatementRow>()

    override suspend fun hasStatementFingerprint(sourceFingerprint: String): Boolean =
        sourceFingerprint in existingStatementFingerprints

    override suspend fun getStatementImportInfo(sourceFingerprint: String): ImportedStatementImportInfo? =
        existingStatementImports[sourceFingerprint]

    override suspend fun hasTransactionFingerprint(transactionFingerprint: String): Boolean =
        transactionFingerprint in existingTransactionFingerprints

    override suspend fun confirmImport(
        sourceFileName: String,
        sourceFingerprint: String,
        rows: List<ApprovedImportedStatementRow>,
        importedAtEpochMillis: Long,
    ): ConfirmImportedStatementResult {
        existingStatementFingerprints += sourceFingerprint
        existingStatementImports.putIfAbsent(
            sourceFingerprint,
            ImportedStatementImportInfo(
                sourceFileName = sourceFileName,
                sourceFingerprint = sourceFingerprint,
                importedAtEpochMillis = importedAtEpochMillis,
            ),
        )
        confirmedRows += rows.filterNot { it.duplicate || it.finalInclusion != DeclarationInclusion.INCLUDED }
        onConfirm?.invoke(rows, importedAtEpochMillis)
        return ConfirmImportedStatementResult(
            importedIncomeCount = confirmedRows.size,
            storedTransactionCount = rows.count { !it.duplicate },
            skippedDuplicateCount = rows.count { it.duplicate },
            excludedCount = rows.count { it.finalInclusion != DeclarationInclusion.INCLUDED },
        )
    }
}

private class StatementImportFakeIncomeRepository(
    private val entries: MutableList<IncomeEntry> = mutableListOf(),
) : IncomeRepository {
    override fun observeAll(): Flow<List<IncomeEntry>> = flowOf(entries.toList())

    override fun observeByMonth(yearMonth: YearMonth): Flow<List<IncomeEntry>> = flowOf(
        entries.filter { YearMonth.from(it.incomeDate) == yearMonth },
    )

    override suspend fun getById(id: Long): IncomeEntry? = entries.firstOrNull { it.id == id }

    override suspend fun upsert(entry: IncomeEntry): Long {
        val index = entries.indexOfFirst { it.id == entry.id && entry.id != 0L }
        if (index >= 0) {
            entries[index] = entry
            return entry.id
        }
        val nextId = if (entry.id != 0L) entry.id else (entries.maxOfOrNull { it.id } ?: 0L) + 1L
        entries += entry.copy(id = nextId)
        return nextId
    }

    override suspend fun deleteById(id: Long) {
        entries.removeAll { it.id == id }
    }
}

private class StatementImportFakeFxRateRepository(
    private val cachedRates: Map<Pair<LocalDate, String>, FxRate> = emptyMap(),
) : FxRateRepository {
    override suspend fun getBestRate(rateDate: LocalDate, currencyCode: String): FxRate? =
        cachedRates[rateDate to currencyCode.uppercase()]

    override suspend fun fetchOfficialRate(rateDate: LocalDate, currencyCode: String): FxRateFetchResult =
        cachedRates[rateDate to currencyCode.uppercase()]
            ?.let(FxRateFetchResult::Success)
            ?: FxRateFetchResult.NotFound

    override suspend fun upsertManualOverride(
        rateDate: LocalDate,
        currencyCode: String,
        units: Int,
        rateToGel: BigDecimal,
    ): FxRate = FxRate(
        rateDate = rateDate,
        currencyCode = currencyCode.uppercase(),
        units = units,
        rateToGel = rateToGel,
        source = FxRateSource.MANUAL_OVERRIDE,
        manualOverride = true,
    )
}
