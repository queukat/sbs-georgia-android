package com.queukat.sbsgeorgia.review

import com.queukat.sbsgeorgia.data.importer.ImportedPdfDocument
import com.queukat.sbsgeorgia.data.importer.StatementDocumentReader
import com.queukat.sbsgeorgia.data.importer.StatementTextExtractor
import com.queukat.sbsgeorgia.domain.model.ApprovedImportedStatementRow
import com.queukat.sbsgeorgia.domain.model.ConfirmImportedStatementResult
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRate
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.ImportedStatementImportInfo
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.StatementMoney
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.repository.FxRateFetchResult
import com.queukat.sbsgeorgia.domain.repository.FxRateRepository
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.repository.StatementImportRepository
import com.queukat.sbsgeorgia.domain.service.GeorgiaTaxBusinessCalendar
import com.queukat.sbsgeorgia.domain.service.MonthUserJourneyState
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationActionPlanner
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import com.queukat.sbsgeorgia.domain.service.ReminderNotificationMessage
import com.queukat.sbsgeorgia.domain.service.ReminderNotificationStrings
import com.queukat.sbsgeorgia.domain.service.ReminderPlanner
import com.queukat.sbsgeorgia.domain.service.ReminderType
import com.queukat.sbsgeorgia.domain.service.SmallBusinessTaxPolicy
import com.queukat.sbsgeorgia.domain.service.tbc.TbcStatementParser
import com.queukat.sbsgeorgia.domain.usecase.CompleteMonthlyDeclarationUseCase
import com.queukat.sbsgeorgia.domain.usecase.ConfirmStatementImportUseCase
import com.queukat.sbsgeorgia.domain.usecase.DetectImportedTaxPaymentCandidatesUseCase
import com.queukat.sbsgeorgia.domain.usecase.LoadStatementImportPreviewUseCase
import com.queukat.sbsgeorgia.domain.usecase.ObserveAllSnapshotsUseCase
import com.queukat.sbsgeorgia.domain.usecase.ObserveDashboardSummaryUseCase
import com.queukat.sbsgeorgia.domain.usecase.ResolveFxForMonthsUseCase
import com.queukat.sbsgeorgia.domain.usecase.ResolveMonthFxUseCase
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementRowUiState
import com.queukat.sbsgeorgia.ui.importstatement.canConfirmImport
import com.queukat.sbsgeorgia.ui.importstatement.excludedCount
import java.io.File
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private fun bd(s: String) = BigDecimal(s)
private val clock = Clock.fixed(Instant.parse("2026-04-20T00:00:00Z"), ZoneId.of("Asia/Tbilisi"))
private val profile = TaxpayerProfile("000000000", "Synthetic taxpayer")
private val config = SmallBusinessStatusConfig(LocalDate.parse("2026-01-01"), bd("1.0"))
private fun entry(date: String, amount: String, currency: String = "GEL", id: Long = 1) = IncomeEntry(
    id, IncomeSourceType.MANUAL, LocalDate.parse(date), bd(amount), currency, "services", "",
    DeclarationInclusion.INCLUDED, if (currency == "GEL") bd(amount) else null, FxRateSource.NONE,
    false, createdAtEpochMillis = 0, updatedAtEpochMillis = 0
)
private val planner = MonthlyDeclarationPlanner(clock, GeorgiaTaxBusinessCalendar())
private fun snapshots(entries: List<IncomeEntry> = emptyList(), c: SmallBusinessStatusConfig = config) =
    planner.buildYearSnapshots(2026, profile, c, entries, emptyList())
private fun march() = snapshots(listOf(entry("2026-03-01", "100")))[2]
private val reminders =
    ReminderConfig(listOf(10, 13, 15), listOf(10, 13, 15), true, true, LocalTime.of(9, 0), ThemeMode.SYSTEM)
private val reminderPlanner = ReminderPlanner(object : ReminderNotificationStrings {
    override fun title(type: ReminderType) = type.name
    override fun body(
        message: ReminderNotificationMessage,
        incomeMonth: YearMonth,
        unresolvedFxCount: Int,
        dueDate: LocalDate
    ) = "$message:$incomeMonth:$dueDate:$unresolvedFxCount"
})
private fun row(review: Boolean = false) = ImportStatementRowUiState(
    "synthetic-1", LocalDate.parse("2026-03-01"), "FOR SOFTWARE SERVICES", null, null,
    StatementMoney(bd("100"), "GEL"), null,
    if (review) DeclarationInclusion.REVIEW_REQUIRED else DeclarationInclusion.INCLUDED,
    if (review) DeclarationInclusion.EXCLUDED else DeclarationInclusion.INCLUDED,
    "100", "GEL", "services", duplicate = false
)
private fun approved(review: Boolean = false, currency: String = "GEL") = ApprovedImportedStatementRow(
    "synthetic-1", LocalDate.parse("2026-03-01"), "FOR SOFTWARE SERVICES", null, null,
    StatementMoney(bd("100"), currency), null,
    if (review) DeclarationInclusion.REVIEW_REQUIRED else DeclarationInclusion.INCLUDED,
    if (review) DeclarationInclusion.EXCLUDED else DeclarationInclusion.INCLUDED,
    bd("100"), currency, "services", false
)
private class IncomeStore(initial: List<IncomeEntry> = emptyList()) : IncomeRepository {
    val state = MutableStateFlow(initial)
    override fun observeAll() = state
    override fun observeByMonth(yearMonth: YearMonth) = state.map {
        it.filter { e ->
            YearMonth.from(e.incomeDate) ==
                yearMonth
        }
    }
    override suspend fun getById(id: Long) = state.value.find { it.id == id }
    override suspend fun upsert(entry: IncomeEntry): Long {
        val id = if (entry.id == 0L) (state.value.maxOfOrNull { it.id } ?: 0) + 1 else entry.id
        state.value = state.value.filterNot { it.id == id } + entry.copy(id = id)
        return id
    }
    override suspend fun deleteById(id: Long) {
        state.value = state.value.filterNot { it.id == id }
    }
}
private class RecordStore : MonthlyDeclarationRepository {
    val state = MutableStateFlow<List<MonthlyDeclarationRecord>>(emptyList())
    override fun observeAll() = state
    override fun observeByMonth(yearMonth: YearMonth) = state.map { it.find { r -> r.yearMonth == yearMonth } }
    override suspend fun upsert(record: MonthlyDeclarationRecord) {
        state.value =
            state.value.filterNot { it.yearMonth == record.yearMonth } + record
    }
}
private class Settings(c: SmallBusinessStatusConfig = config) : SettingsRepository {
    val p = MutableStateFlow<TaxpayerProfile?>(profile)
    val status = MutableStateFlow<SmallBusinessStatusConfig?>(c)
    val r = MutableStateFlow<ReminderConfig?>(reminders)
    override fun observeTaxpayerProfile() = p
    override fun observeStatusConfig() = status
    override fun observeReminderConfig() = r
    override suspend fun upsertTaxpayerProfile(profile: TaxpayerProfile) {
        p.value = profile
    }
    override suspend fun upsertStatusConfig(config: SmallBusinessStatusConfig) {
        status.value = config
    }
    override suspend fun upsertReminderConfig(config: ReminderConfig) {
        r.value = config
    }
}
private class Rates(private val failure: Throwable? = null) : FxRateRepository {
    var fetches = 0
    override suspend fun getBestRate(rateDate: LocalDate, currencyCode: String): FxRate? = null
    override suspend fun getRate(rateDate: LocalDate, currencyCode: String, manualOverride: Boolean): FxRate? = null
    override suspend fun fetchOfficialRate(rateDate: LocalDate, currencyCode: String): FxRateFetchResult {
        fetches++
        failure?.let { throw it }
        return FxRateFetchResult.Success(
            FxRate(rateDate, currencyCode, 1, bd("2.7"), FxRateSource.OFFICIAL_NBG_JSON, false)
        )
    }
    override suspend fun upsertManualOverride(
        rateDate: LocalDate,
        currencyCode: String,
        units: Int,
        rateToGel: BigDecimal
    ) = FxRate(rateDate, currencyCode, units, rateToGel, FxRateSource.MANUAL_OVERRIDE, true)
}
private class ImportStore(private val income: IncomeStore) : StatementImportRepository {
    var confirmations = 0
    var batchReads = 0
    var singleReads = 0
    val fingerprints = mutableSetOf<String>()
    override suspend fun hasStatementFingerprint(sourceFingerprint: String) = false
    override suspend fun getStatementImportInfo(sourceFingerprint: String): ImportedStatementImportInfo? = null
    override suspend fun hasTransactionFingerprint(transactionFingerprint: String): Boolean {
        singleReads++
        return transactionFingerprint in
            fingerprints
    }
    override suspend fun existingTransactionFingerprints(fingerprints: Set<String>): Set<String> {
        batchReads++
        return fingerprints.intersect(this.fingerprints)
    }
    override suspend fun confirmImport(
        sourceFileName: String,
        sourceFingerprint: String,
        rows: List<ApprovedImportedStatementRow>,
        importedAtEpochMillis: Long
    ): ConfirmImportedStatementResult {
        confirmations++
        val fresh = rows.filter { !it.duplicate && fingerprints.add(it.transactionFingerprint) }
        val included = fresh.filter { it.finalInclusion == DeclarationInclusion.INCLUDED }
        included.forEach { r -> income.upsert(entry(r.incomeDate.toString(), r.amount.toPlainString(), r.currency, 0)) }
        return ConfirmImportedStatementResult(
            included.size,
            fresh.size,
            rows.size - fresh.size,
            fresh.size - included.size
        )
    }
}
private fun confirm(store: ImportStore, income: IncomeStore, rates: Rates = Rates()) = ConfirmStatementImportUseCase(
    store,
    ResolveFxForMonthsUseCase(income, ResolveMonthFxUseCase(rates, income, clock)),
    DetectImportedTaxPaymentCandidatesUseCase(),
    clock
)
private suspend fun rejects(block: suspend () -> Unit) {
    var rejected = false
    try {
        block()
    } catch (_: IllegalArgumentException) {
        rejected =
            true
    }
    check(rejected)
}

/** JVM regression checks against production classes. No Android framework or actual PDF renderer is exercised. */
fun main(args: Array<String>) = runBlocking {
    var count = 0
    suspend fun test(name: String, block: suspend () -> Unit) {
        try {
            block()
            count++
            println("PASS $name")
        } catch (t: Throwable) {
            throw AssertionError("FAIL $name", t)
        }
    }
    test("threshold is strict, 500000 still 1%") {
        check(
            SmallBusinessTaxPolicy.rateFor(bd("500000"), bd("1.0")).compareTo(bd("1")) == 0
        )
    }
    test("one tetri above threshold uses 3%") {
        check(
            SmallBusinessTaxPolicy.rateFor(bd("500000.01"), bd("1")) == bd("3")
        )
    }
    test("explicit legacy rate is not silently reinterpreted") {
        check(
            SmallBusinessTaxPolicy.rateFor(bd("600000"), bd("2")) == bd("2")
        )
    }
    test("breach rate applies to whole month and following months") {
        val s = snapshots(listOf(entry("2026-01-01", "499000"), entry("2026-02-01", "1001"), entry("2026-03-01", "10")))
        check(s[0].estimatedTaxAmountGel == bd("4990.00"))
        check(s[1].estimatedTaxAmountGel == bd("30.03"))
        check(
            s[2].estimatedTaxAmountGel == bd("0.30")
        )
    }
    test("ordinary rate resets with new calendar year") {
        val nextPlanner =
            MonthlyDeclarationPlanner(
                Clock.fixed(Instant.parse("2027-02-01T00:00:00Z"), ZoneId.of("Asia/Tbilisi")),
                GeorgiaTaxBusinessCalendar()
            )
        val s = nextPlanner.buildYearSnapshots(
            2027,
            profile,
            config,
            listOf(entry("2026-12-01", "600000"), entry("2027-01-01", "100")),
            emptyList()
        )
        check(s[0].estimatedTaxAmountGel == bd("1.00"))
    }
    test("missing FX is unknown, not zero tax") {
        val s = snapshots(listOf(entry("2026-01-01", "100", "USD")))[0]
        check(
            s.estimatedTaxAmountGel == null
        )
        check(!s.zeroDeclarationSuggested)
    }
    test("previous missing FX blocks later cumulative and tax") {
        val s = snapshots(listOf(entry("2026-01-01", "100", "USD"), entry("2026-03-01", "100")))[2]
        check(s.priorPeriodDataIncomplete)
        check(s.reviewNeeded)
        check(
            s.estimatedTaxAmountGel == null
        )
        check(!MonthlyDeclarationActionPlanner(clock).plan(s).canCopyDeclarationValues)
    }
    test("previous review decision blocks later cumulative") {
        val s = snapshots(
            listOf(entry("2026-01-01", "100").copy(declarationInclusion = DeclarationInclusion.REVIEW_REQUIRED))
        )[2]
        check(s.priorPeriodDataIncomplete)
        check(!s.zeroDeclarationSuggested)
    }
    test("excluded pre-status transaction does not block") {
        val c = config.copy(effectiveDate = LocalDate.parse("2026-03-15"))
        val s = snapshots(
            listOf(entry("2026-03-01", "100").copy(declarationInclusion = DeclarationInclusion.EXCLUDED)),
            c
        )[2]
        check(!s.reviewNeeded)
        check(
            s.estimatedTaxAmountGel == bd("0.00")
        )
    }
    test("unresolved current FX directs user to rates, not add income") {
        val s = snapshots(listOf(entry("2026-03-01", "100", "USD")))[2]
        check(
            MonthlyDeclarationActionPlanner(clock).plan(s).journeyState == MonthUserJourneyState.RESOLVE_FX
        )
    }
    test("null tax blocks completion even without explicit review flag") {
        check(
            !MonthlyDeclarationActionPlanner(
                clock
            ).plan(march().copy(estimatedTaxAmountGel=null, reviewNeeded=false)).canQuickSettleMonth
        )
    }
    test("weekend deadline shifts to Monday") {
        check(
            planner.declarationPeriodFor(
                YearMonth.of(2026, 1),
                config
            ).filingWindow.dueDate == LocalDate.parse("2026-02-16")
        )
    }
    test("review rows cannot be silently imported as excluded") { check(!listOf(row(true)).canConfirmImport()) }
    test("explicit exclusion completes the review decision") {
        check(listOf(row(true).copy(reviewDecisionMade = true)).canConfirmImport())
    }
    test("duplicate-only preview cannot be confirmed") { check(!listOf(row().copy(duplicate=true)).canConfirmImport()) }
    test("invalid included amount cannot be confirmed") { check(!listOf(row().copy(amount="")).canConfirmImport()) }
    test("domain rejects review bypass before persistence") {
        val i = IncomeStore()
        val r = ImportStore(i)
        rejects { confirm(r, i)("synthetic.pdf", "file", listOf(approved(true))) }
        check(
            r.confirmations == 0
        )
    }
    test("domain accepts explicit review exclusion") {
        val i = IncomeStore()
        val r = ImportStore(i)
        val result = confirm(r, i)("synthetic.pdf", "file", listOf(approved(true).copy(reviewDecisionMade = true)))
        check(
            result.importResult.excludedCount == 1
        )
    }
    test("saved import remains successful when FX network fails") {
        val i = IncomeStore()
        val r = ImportStore(i)
        val result =
            confirm(
                r,
                i,
                Rates(java.io.IOException("offline"))
            )("synthetic.pdf", "file", listOf(approved(currency = "USD")))
        check(
            i.state.value.size == 1
        )
        check(result.remainingUnresolvedFxEntryCount == 1)
        check(result.autoResolvedFxEntryCount == 0)
    }
    test("parent cancellation is not swallowed after durable import") {
        val i = IncomeStore()
        val r = ImportStore(i)
        var cancelled = false
        try {
            confirm(
                r,
                i,
                Rates(CancellationException("cancelled"))
            )("synthetic.pdf", "file", listOf(approved(currency = "USD")))
        } catch (
            _: CancellationException
        ) {
            cancelled =
                true
        }
        check(cancelled)
        check(i.state.value.size == 1)
    }
    test("FX resolver groups matching date and currency") {
        val i = IncomeStore(listOf(entry("2026-03-01", "100", "USD", 1), entry("2026-03-01", "200", "USD", 2)))
        val rates = Rates()
        val result = ResolveMonthFxUseCase(rates, i, clock)(i.state.value)
        check(
            rates.fetches == 1
        )
        check(result.resolvedEntryCount == 2)
        check(i.state.value[1].gelEquivalent == bd("540.00"))
    }
    test("excluded entries trigger no FX requests") {
        val i =
            IncomeStore(
                listOf(entry("2026-03-01", "100", "USD").copy(declarationInclusion = DeclarationInclusion.EXCLUDED))
            )
        val rates = Rates()
        ResolveMonthFxUseCase(rates, i, clock)(i.state.value)
        check(
            rates.fetches == 0
        )
    }
    test("one notification, declaration takes priority") {
        val n = reminderPlanner.buildNotifications(LocalDate.parse("2026-04-10"), reminders, march())
        check(
            n.size == 1
        )
        check(n.single().type == ReminderType.DECLARATION)
    }
    test("no reminder before filing window opens") {
        check(reminderPlanner.buildNotifications(LocalDate.parse("2026-03-10"), reminders, march()).isEmpty())
    }
    test("overdue filed month is not requested to file again") {
        val s = march().copy(
            record = MonthlyDeclarationRecord(
                YearMonth.of(2026, 3),
                MonthlyWorkflowStatus.FILED,
                false,
                declarationFiledDate = LocalDate.parse("2026-04-01")
            )
        )
        val n = reminderPlanner.buildNotifications(LocalDate.parse("2026-04-15"), reminders, s)
        check(
            n.single().type == ReminderType.PAYMENT
        )
    }
    test("old unfinished month is not lost on calendar rollover") {
        val old = snapshots()[0].copy(
            record = MonthlyDeclarationRecord(YearMonth.of(2026, 1), MonthlyWorkflowStatus.DRAFT, false)
        )
        val n = reminderPlanner.buildNotificationsForSnapshots(
            LocalDate.parse("2026-04-10"),
            reminders,
            listOf(march().copy(workflowStatus = MonthlyWorkflowStatus.SETTLED), old)
        )
        check(n.single().body.contains("2026-01"))
    }
    test("unrecorded historical zero months are not invented arrears") {
        val n = reminderPlanner.buildNotificationsForSnapshots(
            LocalDate.parse("2026-04-10"),
            reminders,
            listOf(snapshots()[0], march().copy(workflowStatus = MonthlyWorkflowStatus.SETTLED))
        )
        check(n.isEmpty())
    }
    test("current due month takes priority over historical unfinished month") {
        val old = snapshots()[0].copy(
            record = MonthlyDeclarationRecord(YearMonth.of(2026, 1), MonthlyWorkflowStatus.DRAFT, false)
        )
        val n = reminderPlanner.buildNotificationsForSnapshots(
            LocalDate.parse("2026-04-10"),
            reminders,
            listOf(old, march())
        )
        check(n.single().body.contains("2026-03"))
    }
    test("already sent payment asks to check credit, never send twice") {
        val s = march().copy(
            record = MonthlyDeclarationRecord(
                YearMonth.of(2026, 3),
                MonthlyWorkflowStatus.FILED,
                false,
                declarationFiledDate = LocalDate.parse("2026-04-01"),
                paymentSentDate = LocalDate.parse("2026-04-02")
            )
        )
        val n = reminderPlanner.buildNotifications(LocalDate.parse("2026-04-10"), reminders, s)
        check(n.single().body.startsWith("PAYMENT_SENT_CHECK_CREDIT"))
    }
    test("settled month suppresses reminders") {
        val s = march().copy(workflowStatus = MonthlyWorkflowStatus.SETTLED)
        check(reminderPlanner.buildNotifications(LocalDate.parse("2026-04-10"), reminders, s).isEmpty())
    }
    test("next reminder never wraps to a day already passed") {
        check(
            planner.buildDashboardSummary(profile, config, reminders, snapshots(), emptyList()).nextReminderDay == null
        )
    }
    test("January dashboard includes previous December, YTD stays current") {
        val c = Clock.fixed(Instant.parse("2027-01-10T00:00:00Z"), ZoneId.of("Asia/Tbilisi"))
        val p = MonthlyDeclarationPlanner(c, GeorgiaTaxBusinessCalendar())
        val i = IncomeStore(listOf(entry("2026-12-01", "400"), entry("2027-01-01", "25", id = 2)))
        val r = RecordStore()
        val settings = Settings()
        val all = ObserveAllSnapshotsUseCase(settings, i, r, p, c)
        val s = ObserveDashboardSummaryUseCase(settings, r, all, p)().first()
        check(
            s.currentDuePeriod?.period?.incomeMonth == YearMonth.of(2026, 12)
        )
        check(s.ytdIncomeGel == bd("25.00"))
    }
    test("domain completion refuses incomplete FX") {
        val r = RecordStore()
        rejects {
            CompleteMonthlyDeclarationUseCase(r, clock)(snapshots(listOf(entry("2026-03-01", "100", "USD")))[2])
        }
        check(r.state.value.isEmpty())
    }
    test("domain completion refuses future filing window") {
        val r = RecordStore()
        rejects { CompleteMonthlyDeclarationUseCase(r, clock)(snapshots()[3]) }
        check(r.state.value.isEmpty())
    }
    test("zero month completion records filing but invents no payment") {
        val r = RecordStore()
        CompleteMonthlyDeclarationUseCase(r, clock)(snapshots()[2])
        val record = r.state.value.single()
        check(
            record.workflowStatus == MonthlyWorkflowStatus.FILED
        )
        check(record.paymentAmountGel == null)
        check(
            record.paymentSentDate == null
        )
    }
    val fixtures = File(args.firstOrNull() ?: "app/src/test/resources/fixtures")
    fixtures.listFiles()!!.filter {
        it.name.startsWith("tbc_statement") && it.extension == "txt"
    }.sortedBy { it.name }.forEach { f ->
        test("parser fixture ${f.name}") {
            check(TbcStatementParser().parse("synthetic.pdf", "file", f.readText()).rows.isNotEmpty())
        }
    }
    test("preview uses one batch lookup instead of per-row reads") {
        val i = IncomeStore()
        val r = ImportStore(i)
        val text = File(fixtures, "tbc_statement_v1_extracted.txt").readText()
        val loader =
            LoadStatementImportPreviewUseCase(
                StatementDocumentReader {
                    ImportedPdfDocument("synthetic.pdf", "file", byteArrayOf())
                },
                StatementTextExtractor { text },
                TbcStatementParser(),
                r
            )
        val s = loader("local:test")
        check(s.preview!!.rows.isNotEmpty())
        check(r.batchReads == 1)
        check(r.singleReads == 0)
    }
    println(
        "SUCCESS: $count core regression checks. Android UI, Room queries, PDFBox and WorkManager runtime NOT exercised."
    )
}
