package com.queukat.sbsgeorgia.domain.model

data class BackupRestoreResult(
    val reminderConfigImported: Boolean,
    val incomeEntryCount: Int,
    val monthlyRecordCount: Int,
    val importedStatementCount: Int,
    val importedTransactionCount: Int
)
