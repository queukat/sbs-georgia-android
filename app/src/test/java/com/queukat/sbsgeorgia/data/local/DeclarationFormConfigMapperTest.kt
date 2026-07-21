package com.queukat.sbsgeorgia.data.local

import com.queukat.sbsgeorgia.domain.model.DeclarationFormConfig
import com.queukat.sbsgeorgia.domain.model.DeclarationFormField
import org.junit.Assert.assertEquals
import org.junit.Test

class DeclarationFormConfigMapperTest {
    @Test
    fun roundTripPreservesSelectedMonthlyDeclarationField() {
        val config =
            DeclarationFormConfig(
                includeCumulativeIncome = false,
                includeMonthlyIncome = true,
                monthlyIncomeField = DeclarationFormField.MONTHLY_POS_INCOME
            )

        val entity = config.toEntity()

        assertEquals(19, entity.monthlyIncomeFieldNumber)
        assertEquals(config, entity.toDomain())
    }

    @Test(expected = IllegalArgumentException::class)
    fun toDomainRejectsCumulativeFieldAsMonthlyField() {
        DeclarationFormConfigEntity(
            includeCumulativeIncome = true,
            includeMonthlyIncome = true,
            monthlyIncomeFieldNumber = 15
        ).toDomain()
    }
}
