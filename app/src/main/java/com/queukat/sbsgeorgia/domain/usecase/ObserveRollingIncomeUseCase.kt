package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.RollingIncomeSummary
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.service.buildRollingIncomeSummary
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ObserveRollingIncomeUseCase
@Inject
constructor(private val incomeRepository: IncomeRepository) {
    operator fun invoke(asOf: Flow<LocalDate>): Flow<RollingIncomeSummary> = combine(
        incomeRepository.observeAll(),
        asOf
    ) { entries, date -> buildRollingIncomeSummary(entries, date) }
}
