package com.queukat.sbsgeorgia.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase
import com.queukat.sbsgeorgia.data.remote.OfficialFxRemoteDataSource
import com.queukat.sbsgeorgia.data.remote.OfficialFxRemoteResult
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeFxUpdate
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.service.GeorgiaTaxBusinessCalendar
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import com.queukat.sbsgeorgia.domain.usecase.CompleteMonthlyDeclarationUseCase
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Requires the generated Room implementation and an Android device/emulator. */
@RunWith(AndroidJUnit4::class)
class RepositoryConsistencyTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val database = Room.inMemoryDatabaseBuilder(context, SbsGeorgiaDatabase::class.java).build()
    private val clock = Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC)
    private val planner = MonthlyDeclarationPlanner(clock, GeorgiaTaxBusinessCalendar())
    private val income = IncomeRepositoryImpl(database, database.incomeEntryDao(), database.importedTransactionDao())
    private val data = DeclarationDataRepositoryImpl(database, Dispatchers.IO)
    private val settings = SettingsRepositoryImpl(
        database,
        database.taxpayerProfileDao(),
        database.smallBusinessStatusConfigDao(),
        database.reminderConfigDao()
    )
    private val monthly = MonthlyDeclarationRepositoryImpl(database.monthlyDeclarationRecordDao(), database, planner)
    private val profile = TaxpayerProfile("123456789", "Synthetic test")
    private val config = SmallBusinessStatusConfig(LocalDate.of(2026, 1, 1), BigDecimal.ONE)
    private val reminders = ReminderConfig(
        listOf(10, 15),
        listOf(10, 15),
        true,
        true,
        LocalTime.of(9, 0),
        ThemeMode.SYSTEM
    )

    @After
    fun closeDatabase() {
        database.close()
    }

    private fun entry() = IncomeEntry(
        sourceType = IncomeSourceType.MANUAL, incomeDate = LocalDate.of(2026, 3, 15),
        originalAmount = BigDecimal("100"), originalCurrency = "USD", sourceCategory = "Services", note = "",
        declarationInclusion = DeclarationInclusion.INCLUDED, gelEquivalent = null,
        rateSource = FxRateSource.NONE, manualFxOverride = false, createdAtEpochMillis = 0L, updatedAtEpochMillis = 0L
    )

    private fun update(entry: IncomeEntry) = IncomeFxUpdate(
        entry,
        BigDecimal("270.00"),
        FxRateSource.OFFICIAL_NBG_JSON,
        false,
        1L
    )

    @Test
    fun repeatedManualRateReplacesTheSameRow() = runTest {
        val remote = object : OfficialFxRemoteDataSource {
            override suspend fun fetchDailyRates(date: LocalDate) = OfficialFxRemoteResult.NotFound
        }
        val repository = FxRateRepositoryImpl(database.fxRateDao(), remote)
        val day = LocalDate.of(2026, 3, 15)
        repository.upsertManualOverride(day, "USD", 1, BigDecimal("2.7"))
        val id = database.fxRateDao().getAll().single().id
        repository.upsertManualOverride(day, "usd", 100, BigDecimal("275"))
        val row = database.fxRateDao().getAll().single()
        assertEquals(id, row.id)
        assertEquals(100, row.units)
        assertEquals(BigDecimal("275"), row.rateToGel)
    }

    @Test
    fun upsertReturnsExistingIdRatherThanRoomUpdateSentinel() = runTest {
        val id = income.upsert(entry())
        assertTrue(id > 0L)
        assertEquals(id, income.upsert(requireNotNull(income.getById(id)).copy(note = "changed")))
    }

    @Test
    fun fxBatchDoesNotOverwriteEditsOrResurrectDeletedRows() = runTest {
        val changedId = income.upsert(entry())
        val deletedId = income.upsert(entry())
        val validId = income.upsert(entry())
        val changed = requireNotNull(income.getById(changedId))
        val deleted = requireNotNull(income.getById(deletedId))
        val valid = requireNotNull(income.getById(validId))
        income.upsert(changed.copy(originalAmount = BigDecimal("500"), note = "newer"))
        income.deleteById(deletedId)
        assertEquals(1, income.applyFxUpdates(listOf(update(changed), update(deleted), update(valid))))
        assertEquals(BigDecimal("500"), income.getById(changedId)?.originalAmount)
        assertNull(income.getById(changedId)?.gelEquivalent)
        assertNull(income.getById(deletedId))
        assertEquals(BigDecimal("270.00"), income.getById(validId)?.gelEquivalent)
    }

    @Test
    fun readsNeverMixTwoCommittedSettingsRevisions() = runTest {
        settings.upsertSettings(profile.copy(displayName = "0"), config.copy(certificateNumber = "0"), reminders)
        coroutineScope {
            val writer = async(Dispatchers.IO) {
                repeat(50) { index ->
                    val revision = (index + 1).toString()
                    settings.upsertSettings(
                        profile.copy(displayName = revision),
                        config.copy(certificateNumber = revision),
                        reminders
                    )
                }
            }
            repeat(50) {
                val snapshot = data.getData()
                assertEquals(snapshot.profile?.displayName, snapshot.config?.certificateNumber)
            }
            writer.await()
        }
    }

    @Test
    fun quickCompletionRejectsAStaleFinancialSnapshot() = runTest {
        settings.upsertSettings(profile, config, reminders)
        val id = income.upsert(entry().copy(originalCurrency = "GEL", gelEquivalent = BigDecimal("100")))
        val before = data.getData()
        val snapshot = planner.buildYearSnapshots(
            2026,
            before.profile,
            before.config,
            before.entries,
            before.records
        ).first { it.period.incomeMonth == YearMonth.of(2026, 3) }
        income.upsert(
            requireNotNull(income.getById(id)).copy(
                originalAmount = BigDecimal("200"),
                gelEquivalent = BigDecimal("200")
            )
        )
        val result = runCatching { CompleteMonthlyDeclarationUseCase(monthly, clock)(snapshot) }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertNull(monthly.observeByMonth(YearMonth.of(2026, 3)).first())
    }

    @Test
    fun quickCompletionCommitsAnUnchangedSnapshot() = runTest {
        settings.upsertSettings(profile, config, reminders)
        income.upsert(entry().copy(originalCurrency = "GEL", gelEquivalent = BigDecimal("100")))
        val before = data.getData()
        val snapshot = planner.buildYearSnapshots(
            2026,
            before.profile,
            before.config,
            before.entries,
            before.records
        ).first { it.period.incomeMonth == YearMonth.of(2026, 3) }
        CompleteMonthlyDeclarationUseCase(monthly, clock)(snapshot)
        assertNotNull(monthly.observeByMonth(YearMonth.of(2026, 3)).first()?.declarationFiledDate)
    }
}
