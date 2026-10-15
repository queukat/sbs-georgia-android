package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.di.DefaultDispatcher
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.repository.DeclarationData
import com.queukat.sbsgeorgia.domain.repository.DeclarationDataRepository
import com.queukat.sbsgeorgia.domain.service.DateSource
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class DeclarationState(
    val data: DeclarationData,
    val date: LocalDate,
    val snapshots: List<MonthlyDeclarationSnapshot>
)

/** One calculation pipeline, reused by screens, CSV, workers and widgets. */
class ObserveDeclarationStateUseCase
@Inject
constructor(
    private val repository: DeclarationDataRepository,
    private val planner: MonthlyDeclarationPlanner,
    private val clock: Clock,
    private val dateSource: DateSource,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher
) {
    operator fun invoke(): Flow<DeclarationState> = combine(
        repository.observeData(),
        dateSource.observeDate()
    ) { data, date -> buildState(data, date) }
        .distinctUntilChanged()
        .flowOn(dispatcher)

    fun <T> project(transform: (DeclarationState) -> T): Flow<T> = invoke().map(transform).flowOn(dispatcher)

    /** A fresh transaction, not the possibly stale replay of a UI subscription. */
    suspend fun current(): DeclarationState = withContext(dispatcher) {
        buildState(repository.getData(), LocalDate.now(clock))
    }

    private fun buildState(data: DeclarationData, date: LocalDate): DeclarationState {
        // Partition once: do not rescan all income and all workflow records for every year.
        val entriesByYear = data.entries.groupBy { it.incomeDate.year }
        val recordsByYear = data.records.groupBy { it.yearMonth.year }
        val snapshots = collectRelevantSnapshotYears(clock, data.config, data.entries, data.records, date)
            .flatMap { year ->
                planner.buildYearSnapshots(
                    year,
                    data.profile,
                    data.config,
                    entriesByYear[year].orEmpty(),
                    recordsByYear[year].orEmpty(),
                    date
                )
            }
        return DeclarationState(data, date, snapshots)
    }
}
