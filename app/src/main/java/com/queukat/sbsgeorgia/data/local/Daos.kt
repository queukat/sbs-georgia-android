package com.queukat.sbsgeorgia.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface TaxpayerProfileDao {
    @Query("SELECT * FROM taxpayer_profile WHERE singletonId = 1")
    fun observe(): Flow<TaxpayerProfileEntity?>

    @Query("SELECT * FROM taxpayer_profile WHERE singletonId = 1")
    suspend fun get(): TaxpayerProfileEntity?

    @Upsert
    suspend fun upsert(entity: TaxpayerProfileEntity)

    @Query("DELETE FROM taxpayer_profile")
    suspend fun clear()
}

@Dao
interface SmallBusinessStatusConfigDao {
    @Query("SELECT * FROM small_business_status_config WHERE singletonId = 1")
    fun observe(): Flow<SmallBusinessStatusConfigEntity?>

    @Query("SELECT * FROM small_business_status_config WHERE singletonId = 1")
    suspend fun get(): SmallBusinessStatusConfigEntity?

    @Upsert
    suspend fun upsert(entity: SmallBusinessStatusConfigEntity)

    @Query("DELETE FROM small_business_status_config")
    suspend fun clear()
}

@Dao
interface ReminderConfigDao {
    @Query("SELECT * FROM reminder_config WHERE singletonId = 1")
    fun observe(): Flow<ReminderConfigEntity?>

    @Query("SELECT * FROM reminder_config WHERE singletonId = 1")
    suspend fun get(): ReminderConfigEntity?

    @Upsert
    suspend fun upsert(entity: ReminderConfigEntity)

    @Query("DELETE FROM reminder_config")
    suspend fun clear()
}

@Dao
interface IncomeEntryDao {
    @Query("SELECT * FROM income_entry ORDER BY incomeDate DESC, id DESC")
    fun observeAll(): Flow<List<IncomeEntryEntity>>

    @Query("SELECT * FROM income_entry ORDER BY incomeDate ASC, id ASC")
    suspend fun getAll(): List<IncomeEntryEntity>

    @Query(
        """
        SELECT * FROM income_entry
        WHERE incomeDate >= :startDate AND incomeDate <= :endDate
        ORDER BY incomeDate DESC, id DESC
        """,
    )
    fun observeByMonth(startDate: String, endDate: String): Flow<List<IncomeEntryEntity>>

    @Query("SELECT * FROM income_entry WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): IncomeEntryEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM income_entry
            WHERE sourceTransactionFingerprint = :transactionFingerprint
            LIMIT 1
        )
        """,
    )
    suspend fun existsBySourceTransactionFingerprint(transactionFingerprint: String): Boolean

    @Upsert
    suspend fun upsert(entity: IncomeEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<IncomeEntryEntity>)

    @Query("DELETE FROM income_entry WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM income_entry")
    suspend fun clear()
}

@Dao
interface MonthlyDeclarationRecordDao {
    @Query("SELECT * FROM monthly_declaration_record ORDER BY year DESC, month DESC")
    fun observeAll(): Flow<List<MonthlyDeclarationRecordEntity>>

    @Query("SELECT * FROM monthly_declaration_record ORDER BY year ASC, month ASC")
    suspend fun getAll(): List<MonthlyDeclarationRecordEntity>

    @Query("SELECT * FROM monthly_declaration_record WHERE periodKey = :periodKey LIMIT 1")
    fun observeByPeriod(periodKey: String): Flow<MonthlyDeclarationRecordEntity?>

    @Upsert
    suspend fun upsert(entity: MonthlyDeclarationRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MonthlyDeclarationRecordEntity>)

    @Query("DELETE FROM monthly_declaration_record")
    suspend fun clear()
}

@Dao
interface FxRateDao {
    @Query("SELECT * FROM fx_rate ORDER BY rateDate ASC, currencyCode ASC, id ASC")
    suspend fun getAll(): List<FxRateEntity>

    @Query(
        """
        SELECT * FROM fx_rate
        WHERE rateDate = :rateDate AND currencyCode = :currencyCode
        ORDER BY manualOverride DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun getBestRate(rateDate: LocalDate, currencyCode: String): FxRateEntity?

    @Query(
        """
        SELECT * FROM fx_rate
        WHERE rateDate = :rateDate AND currencyCode = :currencyCode AND manualOverride = :manualOverride
        LIMIT 1
        """,
    )
    suspend fun getRate(
        rateDate: LocalDate,
        currencyCode: String,
        manualOverride: Boolean,
    ): FxRateEntity?

    @Upsert
    suspend fun upsert(entity: FxRateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FxRateEntity>)

    @Query("DELETE FROM fx_rate")
    suspend fun clear()
}

@Dao
interface ImportedStatementDao {
    @Query("SELECT * FROM imported_statement ORDER BY importedAtEpochMillis ASC, id ASC")
    suspend fun getAll(): List<ImportedStatementEntity>

    @Query("SELECT * FROM imported_statement WHERE sourceFingerprint = :sourceFingerprint LIMIT 1")
    suspend fun getBySourceFingerprint(sourceFingerprint: String): ImportedStatementEntity?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM imported_statement
            WHERE sourceFingerprint = :sourceFingerprint
            LIMIT 1
        )
        """,
    )
    suspend fun existsBySourceFingerprint(sourceFingerprint: String): Boolean

    @Insert
    suspend fun insert(entity: ImportedStatementEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ImportedStatementEntity>)

    @Query("DELETE FROM imported_statement")
    suspend fun clear()
}

@Dao
interface ImportedTransactionDao {
    @Query("SELECT * FROM imported_transaction ORDER BY statementId ASC, id ASC")
    suspend fun getAll(): List<ImportedTransactionEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM imported_transaction
            WHERE transactionFingerprint = :transactionFingerprint
            LIMIT 1
        )
        """,
    )
    suspend fun existsByFingerprint(transactionFingerprint: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entities: List<ImportedTransactionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(entities: List<ImportedTransactionEntity>)

    @Query("DELETE FROM imported_transaction")
    suspend fun clear()
}
