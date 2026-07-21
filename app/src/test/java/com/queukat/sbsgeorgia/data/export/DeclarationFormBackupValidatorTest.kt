package com.queukat.sbsgeorgia.data.export

import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclarationFormBackupValidatorTest {
    private val validator = BackupValidator(Json { ignoreUnknownKeys = true })

    @Test
    fun buildRestorePlanRejectsDeclarationFieldOutsideMonthlyIncomeFields() {
        val error =
            runCatching {
                validator.buildRestorePlan(
                    content =
                    """
                        {
                          "formatVersion": 1,
                          "exportedAtEpochMillis": 1,
                          "declarationFormConfig": {
                            "includeCumulativeIncome": true,
                            "includeMonthlyIncome": true,
                            "monthlyIncomeFieldNumber": 15
                          }
                        }
                    """.trimIndent()
                )
            }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("invalid declaration form config"))
    }
}
