package com.queukat.sbsgeorgia.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclarationFormConfigTest {
    @Test
    fun defaultsShowCumulativeAndMonthlyIncomeWithNonCashInField20() {
        val config = DeclarationFormConfig()

        assertTrue(config.includeCumulativeIncome)
        assertTrue(config.includeMonthlyIncome)
        assertEquals(DeclarationFormField.MONTHLY_NON_CASH_INCOME, config.monthlyIncomeField)
        assertEquals(20, config.monthlyIncomeField.fieldNumber)
    }

    @Test
    fun monthlyIncomeFieldsContainEverySupportedMonthlyDestination() {
        assertEquals(
            listOf(
                DeclarationFormField.MONTHLY_CASH_REGISTER_INCOME,
                DeclarationFormField.MONTHLY_POS_INCOME,
                DeclarationFormField.MONTHLY_NON_CASH_INCOME,
                DeclarationFormField.MONTHLY_OTHER_INCOME
            ),
            MONTHLY_INCOME_FIELDS
        )
    }

    @Test
    fun cumulativeFieldCannotBeSelectedAsMonthlyDestination() {
        assertThrows(IllegalArgumentException::class.java) {
            DeclarationFormConfig(
                monthlyIncomeField = DeclarationFormField.CUMULATIVE_INCOME
            )
        }
    }
}
