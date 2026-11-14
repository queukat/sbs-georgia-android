package com.queukat.sbsgeorgia.domain.repository

import com.queukat.sbsgeorgia.domain.model.BackupRestoreResult

interface AppBackupRepository {
    suspend fun exportJson(): String

    suspend fun importJson(content: String): BackupRestoreResult
}
