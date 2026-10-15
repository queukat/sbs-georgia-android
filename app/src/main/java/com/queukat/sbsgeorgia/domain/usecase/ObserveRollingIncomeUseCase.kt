package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.di.DefaultDispatcher
import com.queukat.sbsgeorgia.domain.model.RollingIncomeSummary
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.service.buildRollingIncomeSummary
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

class ObserveRollingIncomeUseCase
@Inject
constructor(
    private val incomeRepository: IncomeRepository,
    @param:DefaultDispatcher private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    operator fun invoke(asOf: Flow<LocalDate>): Flow<RollingIncomeSummary> = combine(
        incomeRepository.observeAll(),
        asOf
    ) { entries, date -> buildRollingIncomeSummary(entries, date) }.flowOn(dispatcher)
}
