package com.queukat.sbsgeorgia.di

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.queukat.sbsgeorgia.data.importer.AndroidStatementDocumentReader
import com.queukat.sbsgeorgia.data.importer.PdfBoxStatementTextExtractor
import com.queukat.sbsgeorgia.data.importer.StatementDocumentReader
import com.queukat.sbsgeorgia.data.importer.StatementTextExtractor
import com.queukat.sbsgeorgia.data.export.AndroidTextDocumentStore
import com.queukat.sbsgeorgia.data.export.TextDocumentStore
import com.queukat.sbsgeorgia.data.local.FxRateDao
import com.queukat.sbsgeorgia.data.local.IncomeEntryDao
import com.queukat.sbsgeorgia.data.local.ImportedStatementDao
import com.queukat.sbsgeorgia.data.local.ImportedTransactionDao
import com.queukat.sbsgeorgia.data.local.MonthlyDeclarationRecordDao
import com.queukat.sbsgeorgia.data.local.ReminderConfigDao
import com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase
import com.queukat.sbsgeorgia.data.local.SmallBusinessStatusConfigDao
import com.queukat.sbsgeorgia.data.local.TaxpayerProfileDao
import com.queukat.sbsgeorgia.data.remote.NbgFxRemoteDataSource
import com.queukat.sbsgeorgia.data.remote.OfficialFxRemoteDataSource
import com.queukat.sbsgeorgia.data.repository.AppPreferencesRepositoryImpl
import com.queukat.sbsgeorgia.data.repository.FxRateRepositoryImpl
import com.queukat.sbsgeorgia.data.repository.IncomeRepositoryImpl
import com.queukat.sbsgeorgia.data.repository.MonthlyDeclarationRepositoryImpl
import com.queukat.sbsgeorgia.data.repository.SettingsRepositoryImpl
import com.queukat.sbsgeorgia.data.repository.StatementImportRepositoryImpl
import com.queukat.sbsgeorgia.domain.repository.AppPreferencesRepository
import com.queukat.sbsgeorgia.domain.repository.FxRateRepository
import com.queukat.sbsgeorgia.domain.repository.IncomeRepository
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.repository.StatementImportRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {
    @Binds
    abstract fun bindStatementDocumentReader(impl: AndroidStatementDocumentReader): StatementDocumentReader

    @Binds
    abstract fun bindStatementTextExtractor(impl: PdfBoxStatementTextExtractor): StatementTextExtractor

    @Binds
    abstract fun bindTextDocumentStore(impl: AndroidTextDocumentStore): TextDocumentStore

    @Binds
    abstract fun bindAppPreferencesRepository(impl: AppPreferencesRepositoryImpl): AppPreferencesRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindIncomeRepository(impl: IncomeRepositoryImpl): IncomeRepository

    @Binds
    abstract fun bindMonthlyDeclarationRepository(impl: MonthlyDeclarationRepositoryImpl): MonthlyDeclarationRepository

    @Binds
    abstract fun bindFxRateRepository(impl: FxRateRepositoryImpl): FxRateRepository

    @Binds
    abstract fun bindStatementImportRepository(impl: StatementImportRepositoryImpl): StatementImportRepository

    @Binds
    abstract fun bindOfficialFxRemoteDataSource(impl: NbgFxRemoteDataSource): OfficialFxRemoteDataSource
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("sbs_georgia_prefs", Context.MODE_PRIVATE)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SbsGeorgiaDatabase =
        Room.databaseBuilder(
            context,
            SbsGeorgiaDatabase::class.java,
            "sbs_georgia.db",
        )
            .addMigrations(
                SbsGeorgiaDatabase.MIGRATION_1_2,
                SbsGeorgiaDatabase.MIGRATION_2_3,
                SbsGeorgiaDatabase.MIGRATION_3_4,
            )
            .build()

    @Provides
    fun provideTaxpayerProfileDao(database: SbsGeorgiaDatabase): TaxpayerProfileDao = database.taxpayerProfileDao()

    @Provides
    fun provideSmallBusinessStatusConfigDao(database: SbsGeorgiaDatabase): SmallBusinessStatusConfigDao =
        database.smallBusinessStatusConfigDao()

    @Provides
    fun provideReminderConfigDao(database: SbsGeorgiaDatabase): ReminderConfigDao = database.reminderConfigDao()

    @Provides
    fun provideIncomeEntryDao(database: SbsGeorgiaDatabase): IncomeEntryDao = database.incomeEntryDao()

    @Provides
    fun provideMonthlyDeclarationRecordDao(database: SbsGeorgiaDatabase): MonthlyDeclarationRecordDao =
        database.monthlyDeclarationRecordDao()

    @Provides
    fun provideFxRateDao(database: SbsGeorgiaDatabase): FxRateDao = database.fxRateDao()

    @Provides
    fun provideImportedStatementDao(database: SbsGeorgiaDatabase): ImportedStatementDao = database.importedStatementDao()

    @Provides
    fun provideImportedTransactionDao(database: SbsGeorgiaDatabase): ImportedTransactionDao =
        database.importedTransactionDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
