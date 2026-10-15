package com.queukat.sbsgeorgia.domain.repository

import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import kotlinx.coroutines.flow.Flow

/** All calculation inputs from one committed database snapshot. */
data class DeclarationData(
    val profile: TaxpayerProfile?,
    val config: SmallBusinessStatusConfig?,
    val reminders: ReminderConfig?,
    val entries: List<IncomeEntry>,
    val records: List<MonthlyDeclarationRecord>
)

interface DeclarationDataRepository {
    fun observeData(): Flow<DeclarationData>

    suspend fun getData(): DeclarationData
}
