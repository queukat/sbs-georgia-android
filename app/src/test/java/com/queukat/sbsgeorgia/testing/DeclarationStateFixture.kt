package com.queukat.sbsgeorgia.testing

import com.queukat.sbsgeorgia.domain.repository.DeclarationData
import com.queukat.sbsgeorgia.domain.repository.DeclarationDataRepository
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.service.DateSource
import com.queukat.sbsgeorgia.domain.service.MonthlyDeclarationPlanner
import com.queukat.sbsgeorgia.domain.usecase.ObserveDeclarationStateUseCase
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf

/** Adapts existing single-threaded unit-test stores. Production uses a Room transaction. */
fun declarationStateForTest(
    settingsRepository: SettingsRepository,
    incomeRepository: IncomeRepository,
    monthlyDeclarationRepository: MonthlyDeclarationRepository,
    planner: MonthlyDeclarationPlanner,
    clock: Clock
): ObserveDeclarationStateUseCase {
    val repository = object : DeclarationDataRepository {
        override fun observeData() = combine(
            settingsRepository.observeTaxpayerProfile(),
            settingsRepository.observeStatusConfig(),
            settingsRepository.observeReminderConfig(),
            incomeRepository.observeAll(),
            monthlyDeclarationRepository.observeAll()
        ) { profile, config, reminders, entries, records ->
            DeclarationData(profile, config, reminders, entries, records)
        }

        override suspend fun getData() = observeData().first()
    }
    val dateSource = object : DateSource {
        override fun observeDate() = flowOf(LocalDate.now(clock))
    }
    return ObserveDeclarationStateUseCase(repository, planner, clock, dateSource, Dispatchers.Unconfined)
}
