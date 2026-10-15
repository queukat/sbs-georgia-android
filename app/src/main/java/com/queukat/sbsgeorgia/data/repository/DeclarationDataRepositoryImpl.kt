package com.queukat.sbsgeorgia.data.repository

import androidx.room.withTransaction
import com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase
import com.queukat.sbsgeorgia.data.local.toDomain
import com.queukat.sbsgeorgia.di.IoDispatcher
import com.queukat.sbsgeorgia.domain.repository.DeclarationData
import com.queukat.sbsgeorgia.domain.repository.DeclarationDataRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class DeclarationDataRepositoryImpl
@Inject
constructor(
    private val database: SbsGeorgiaDatabase,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : DeclarationDataRepository {
    override fun observeData(): Flow<DeclarationData> = database.invalidationTracker.createFlow(
        "taxpayer_profile",
        "small_business_status_config",
        "reminder_config",
        "income_entry",
        "monthly_declaration_record"
    ).map { getData() }.distinctUntilChanged().flowOn(ioDispatcher)

    override suspend fun getData(): DeclarationData = withContext(ioDispatcher) {
        database.withTransaction {
            DeclarationData(
                profile = database.taxpayerProfileDao().get()?.toDomain(),
                config = database.smallBusinessStatusConfigDao().get()?.toDomain(),
                reminders = database.reminderConfigDao().get()?.toDomain(),
                entries = database.incomeEntryDao().getAll().map { it.toDomain() },
                records = database.monthlyDeclarationRecordDao().getAll().map { it.toDomain() }
            )
        }
    }
}
