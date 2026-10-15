package com.queukat.sbsgeorgia.review

import com.queukat.sbsgeorgia.data.io.readBytesLimited
import com.queukat.sbsgeorgia.data.local.FxRateDao
import com.queukat.sbsgeorgia.data.local.FxRateEntity
import com.queukat.sbsgeorgia.data.remote.OfficialFxRemoteDataSource
import com.queukat.sbsgeorgia.data.remote.OfficialFxRemoteResult
import com.queukat.sbsgeorgia.data.repository.FxRateRepositoryImpl
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRate
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeFxUpdate
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.repository.DeclarationData
import com.queukat.sbsgeorgia.domain.repository.DeclarationDataRepository
import com.queukat.sbsgeorgia.domain.repository.FxRateFetchResult
import com.queukat.sbsgeorgia.domain.repository.FxRateRepository
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.service.DateSource
import com.queukat.sbsgeorgia.domain.service.GeorgiaTaxBusinessCalendar
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import com.queukat.sbsgeorgia.domain.usecase.ApplyManualFxOverrideUseCase
import com.queukat.sbsgeorgia.domain.usecase.ObserveCurrentYearSnapshotsUseCase
import com.queukat.sbsgeorgia.domain.usecase.ObserveDeclarationStateUseCase
import com.queukat.sbsgeorgia.domain.usecase.ObserveMonthDetailUseCase
import com.queukat.sbsgeorgia.domain.usecase.ResolveMonthFxUseCase
import com.queukat.sbsgeorgia.domain.usecase.UpsertManualIncomeEntryUseCase
import com.queukat.sbsgeorgia.domain.usecase.loadMatchingFxRateDetails
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** Runs real use cases with deterministic repositories; does not simulate Android or generated Room SQL. */
object HardeningRegressionChecks {
    private val clock = Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC)
    private val day = LocalDate.of(2026, 3, 15)
    private val rate = FxRate(day, "USD", 1, BigDecimal("2.7"), FxRateSource.OFFICIAL_NBG_JSON, false)

    private fun entry(id: Long = 1L) = IncomeEntry(
        id = id, sourceType = IncomeSourceType.MANUAL, incomeDate = day,
        originalAmount = BigDecimal("100"), originalCurrency = "USD", sourceCategory = "Services", note = "",
        declarationInclusion = DeclarationInclusion.INCLUDED, gelEquivalent = null,
        rateSource = FxRateSource.NONE, manualFxOverride = false,
        createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L
    )

    private class IncomeStore(initial: List<IncomeEntry>) : IncomeRepository {
        val rows = MutableStateFlow(initial)
        var batchWrites = 0
        override fun observeAll() = rows
        override fun observeByMonth(yearMonth: YearMonth) = rows.map { all ->
            all.filter { YearMonth.from(it.incomeDate) == yearMonth }
        }
        override suspend fun getById(id: Long) = rows.value.find { it.id == id }
        override suspend fun upsert(entry: IncomeEntry): Long {
            rows.value = rows.value.filterNot { it.id == entry.id } + entry
            return entry.id
        }
        override suspend fun deleteById(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
        }
        override suspend fun applyFxUpdates(updates: List<IncomeFxUpdate>): Int {
            batchWrites++
            val byId = updates.associateBy { it.expected.id }
            var applied = 0
            rows.value = rows.value.map { current ->
                val update = byId[current.id]
                if (update != null && current == update.expected) {
                    applied++
                    update.applyTo(current)
                } else {
                    current
                }
            }
            return applied
        }
    }

    private class Rates(private val value: FxRate, private val beforeWrite: suspend () -> Unit = {}) :
        FxRateRepository {
        var reads = 0
        var fetches = 0
        override suspend fun getBestRate(rateDate: LocalDate, currencyCode: String): FxRate? = null
        override suspend fun getRate(rateDate: LocalDate, currencyCode: String, manualOverride: Boolean): FxRate {
            reads++
            return value
        }
        override suspend fun fetchOfficialRate(rateDate: LocalDate, currencyCode: String): FxRateFetchResult {
            fetches++
            beforeWrite()
            return FxRateFetchResult.Success(value)
        }
        override suspend fun upsertManualOverride(
            rateDate: LocalDate,
            currencyCode: String,
            units: Int,
            rateToGel: BigDecimal
        ): FxRate {
            beforeWrite()
            return FxRate(rateDate, currencyCode, units, rateToGel, FxRateSource.MANUAL_OVERRIDE, true)
        }
    }

    /** Models PK-based upsert and a unique natural key; deliberately not a Room replacement. */
    private class RatesDao : FxRateDao {
        val rows = mutableListOf<FxRateEntity>()
        override suspend fun getAll() = rows.toList()
        override suspend fun getBestRate(rateDate: LocalDate, currencyCode: String) =
            getRate(rateDate, currencyCode, true) ?: getRate(rateDate, currencyCode, false)
        override suspend fun getRate(rateDate: LocalDate, currencyCode: String, manualOverride: Boolean) = rows.find {
            it.rateDate == rateDate && it.currencyCode == currencyCode && it.manualOverride == manualOverride
        }
        override suspend fun upsert(entity: FxRateEntity): Long {
            val index = rows.indexOfFirst { entity.id != 0L && it.id == entity.id }
            if (index >= 0) {
                rows[index] = entity
                return -1
            }
            check(getRate(entity.rateDate, entity.currencyCode, entity.manualOverride) == null) {
                "Natural-key collision: an update must preserve the existing primary key."
            }
            val id = (rows.maxOfOrNull { it.id } ?: 0L) + 1L
            rows += entity.copy(id = id)
            return id
        }
        override suspend fun insertAll(entities: List<FxRateEntity>) {
            entities.forEach { upsert(it) }
        }
        override suspend fun clear() = rows.clear()
    }

    private class DataStore(initial: DeclarationData) : DeclarationDataRepository {
        val state = MutableStateFlow(initial)
        var freshReads = 0
        override fun observeData() = state
        override suspend fun getData(): DeclarationData {
            freshReads++
            return state.value
        }
    }

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        var count = 0
        suspend fun test(name: String, block: suspend () -> Unit) {
            try {
                withTimeout(10_000) { block() }
                count++
                println("PASS $name")
            } catch (error: Throwable) {
                throw AssertionError("FAILED: $name", error)
            }
        }
        test("FX fetch cannot overwrite an edit made while awaiting the rate") {
            val original = entry()
            val store = IncomeStore(listOf(original))
            val edited = original.copy(originalAmount = BigDecimal("500"), note = "new", updatedAtEpochMillis = 1L)
            val result = ResolveMonthFxUseCase(Rates(rate) { store.upsert(edited) }, store, clock)(listOf(original))
            check(store.rows.value.single() == edited)
            check(result.resolvedEntryCount == 0 && result.skippedChangedEntryCount == 1)
        }
        test("FX fetch cannot resurrect a deleted income") {
            val original = entry()
            val store = IncomeStore(listOf(original))
            val result = ResolveMonthFxUseCase(Rates(rate) { store.deleteById(1L) }, store, clock)(listOf(original))
            check(store.rows.value.isEmpty() && result.skippedChangedEntryCount == 1)
        }
        test("500 entries sharing a date and currency use one FX fetch and one write batch") {
            val entries = (1L..500L).map { entry(it) }
            val store = IncomeStore(entries)
            val rates = Rates(rate)
            val result = ResolveMonthFxUseCase(rates, store, clock)(entries)
            check(result.resolvedEntryCount == 500 && rates.fetches == 1 && store.batchWrites == 1)
            check(store.rows.value.all { it.gelEquivalent == BigDecimal("270.00") })
        }
        test("unaffected entries still resolve when one entry changes") {
            val entries = listOf(entry(), entry(2L))
            val store = IncomeStore(entries)
            val result = ResolveMonthFxUseCase(
                Rates(rate) { store.deleteById(1L) },
                store,
                clock
            )(entries)
            check(result.resolvedEntryCount == 1 && result.skippedChangedEntryCount == 1)
            check(store.rows.value.single().id == 2L)
        }
        test("excluded and review-required income do not request automatic FX") {
            val entries = listOf(
                entry().copy(declarationInclusion = DeclarationInclusion.EXCLUDED),
                entry(2L).copy(declarationInclusion = DeclarationInclusion.REVIEW_REQUIRED)
            )
            val rates = Rates(rate)
            ResolveMonthFxUseCase(rates, IncomeStore(entries), clock)(entries)
            check(rates.fetches == 0)
        }
        test("FX display queries once for 500 matching rows") {
            val entries = (1L..500L).map { entry(it).copy(gelEquivalent = BigDecimal("270.00")) }
            val rates = Rates(rate)
            check(loadMatchingFxRateDetails(entries, rates).size == 500)
            check(rates.reads == 1)
        }
        test("incompatible current cache rate is not presented as a historical applied rate") {
            val rates = Rates(rate.copy(rateToGel = BigDecimal("2.8")))
            val resolved = entry().copy(gelEquivalent = BigDecimal("270.00"))
            check(loadMatchingFxRateDetails(listOf(resolved), rates).isEmpty())
        }
        test("manual FX cannot overwrite an edit made while saving the override") {
            val original = entry()
            val store = IncomeStore(listOf(original))
            val changed = original.copy(note = "newer note")
            val useCase = ApplyManualFxOverrideUseCase(Rates(rate) { store.upsert(changed) }, store, clock)
            check(runCatching { useCase(1L, 1, BigDecimal("2.8")) }.exceptionOrNull() is IllegalStateException)
            check(store.rows.value.single() == changed)
        }
        test("invalid manual FX is rejected before any write") {
            val store = IncomeStore(listOf(entry()))
            val useCase = ApplyManualFxOverrideUseCase(Rates(rate), store, clock)
            check(runCatching { useCase(1L, 0, BigDecimal.ONE) }.isFailure)
            check(runCatching { useCase(1L, 1, BigDecimal.ZERO) }.isFailure)
            check(store.batchWrites == 0)
        }
        test("zero and negative manual income are rejected at the domain boundary") {
            val store = IncomeStore(emptyList())
            val useCase = UpsertManualIncomeEntryUseCase(store)
            check(runCatching { useCase(entry().copy(originalAmount = BigDecimal.ZERO)) }.isFailure)
            check(runCatching { useCase(entry().copy(originalAmount = BigDecimal("-1"))) }.isFailure)
            check(store.rows.value.isEmpty())
        }
        test("repeated manual override preserves PK and actually replaces its value") {
            val dao = RatesDao()
            val remote = object : OfficialFxRemoteDataSource {
                override suspend fun fetchDailyRates(date: LocalDate) = OfficialFxRemoteResult.NotFound
            }
            val repository = FxRateRepositoryImpl(dao, remote)
            repository.upsertManualOverride(day, "usd", 1, BigDecimal("2.7"))
            val id = dao.rows.single().id
            val result = repository.upsertManualOverride(day, "USD", 100, BigDecimal("275"))
            check(result.units == 100 && result.rateToGel == BigDecimal("275"))
            check(dao.rows.single().id == id && dao.rows.single().rateToGel == BigDecimal("275"))
        }
        val initial = DeclarationData(
            TaxpayerProfile("123456789", "Synthetic Test"),
            SmallBusinessStatusConfig(LocalDate.of(2024, 1, 1), BigDecimal.ONE),
            null,
            listOf(entry().copy(originalCurrency = "GEL", gelEquivalent = BigDecimal("100"))),
            emptyList()
        )
        val planner = MonthlyDeclarationPlanner(clock, GeorgiaTaxBusinessCalendar())
        fun pipeline(store: DataStore, date: MutableStateFlow<LocalDate>) = ObserveDeclarationStateUseCase(
            store,
            planner,
            clock,
            object : DateSource {
                override fun observeDate() = date
            },
            Dispatchers.Unconfined
        )
        test("calendar rollover updates current-year snapshots without database writes") {
            coroutineScope {
                val store = DataStore(initial)
                val date = MutableStateFlow(LocalDate.of(2026, 12, 31))
                val outputs = Channel<List<MonthlyDeclarationSnapshot>>(Channel.UNLIMITED)
                val job = launch {
                    ObserveCurrentYearSnapshotsUseCase(pipeline(store, date))().collect { outputs.send(it) }
                }
                try {
                    check(outputs.receive().all { it.period.incomeMonth.year == 2026 })
                    date.value = LocalDate.of(2027, 1, 1)
                    val next = outputs.receive()
                    check(next.isNotEmpty() && next.all { it.period.incomeMonth.year == 2027 })
                    check(store.state.value == initial)
                } finally {
                    job.cancel()
                }
            }
        }
        test("month details use entries and totals from the same data snapshot") {
            val store = DataStore(initial)
            val pipe = pipeline(store, MutableStateFlow(LocalDate.now(clock)))
            val (snapshot, entries) = ObserveMonthDetailUseCase(pipe)(YearMonth.of(2026, 3)).first()
            check(entries.size == 1)
            check(snapshot!!.graph20TotalGel.compareTo(entries.single().originalAmount) == 0)
        }
        test("fresh reads for widgets and workers do not rely on a UI subscription") {
            val store = DataStore(initial)
            val pipe = pipeline(store, MutableStateFlow(LocalDate.now(clock)))
            pipe.current()
            store.state.value = initial.copy(entries = emptyList())
            check(pipe.current().data.entries.isEmpty() && store.freshReads == 2)
        }
        test("partitioned calculations preserve planner results and empty intermediate years") {
            val store = DataStore(initial)
            val snapshots = pipeline(store, MutableStateFlow(LocalDate.now(clock))).current().snapshots
            val expected = (2024..2026).reversed().flatMap { year ->
                planner.buildYearSnapshots(year, initial.profile, initial.config, initial.entries, initial.records)
            }
            check(snapshots == expected)
            check(snapshots.count { it.period.incomeMonth.year == 2025 } == 12)
        }
        test("bounded input accepts exactly the configured limit") {
            val bytes = ByteArray(8192) { (it % 127).toByte() }
            check(ByteArrayInputStream(bytes).readBytesLimited(bytes.size).contentEquals(bytes))
        }
        test("bounded input rejects one byte over the limit") {
            check(runCatching { ByteArrayInputStream(ByteArray(8193)).readBytesLimited(8192) }.isFailure)
        }
        test("zero-byte limit accepts empty input but rejects content") {
            check(ByteArrayInputStream(byteArrayOf()).readBytesLimited(0).isEmpty())
            check(runCatching { ByteArrayInputStream(byteArrayOf(1)).readBytesLimited(0) }.isFailure)
        }
        test("provider returning zero bytes cannot spin indefinitely or bypass the limit") {
            val source = object : InputStream() {
                var remaining = 4
                override fun read(buffer: ByteArray, offset: Int, length: Int) = if (remaining > 0) 0 else -1
                override fun read() = if (remaining-- > 0) 65 else -1
            }
            check(runCatching { source.readBytesLimited(3) }.isFailure)
        }
        println("SUCCESS: $count hardening regression checks. Room generation and Android runtime NOT exercised.")
    }
}
