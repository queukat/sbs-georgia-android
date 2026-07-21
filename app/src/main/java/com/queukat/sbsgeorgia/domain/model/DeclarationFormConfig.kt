package com.queukat.sbsgeorgia.domain.model

enum class DeclarationFormField(val fieldNumber: Int, val persistedCode: String) {
    CUMULATIVE_INCOME(15, "cumulative_income"),
    MONTHLY_CASH_REGISTER_INCOME(18, "monthly_cash_register_income"),
    MONTHLY_POS_INCOME(19, "monthly_pos_income"),
    MONTHLY_NON_CASH_INCOME(20, "monthly_non_cash_income"),
    MONTHLY_OTHER_INCOME(21, "monthly_other_income");

    companion object {
        fun fromFieldNumber(fieldNumber: Int): DeclarationFormField? =
            entries.firstOrNull { it.fieldNumber == fieldNumber }
    }
}

data class DeclarationFormConfig(
    val includeCumulativeIncome: Boolean = true,
    val includeMonthlyIncome: Boolean = true,
    val monthlyIncomeField: DeclarationFormField = DEFAULT_MONTHLY_INCOME_FIELD
) {
    init {
        require(monthlyIncomeField in MONTHLY_INCOME_FIELDS) {
            "Field ${monthlyIncomeField.fieldNumber} is not a monthly income destination."
        }
    }
}

val DEFAULT_MONTHLY_INCOME_FIELD: DeclarationFormField =
    DeclarationFormField.MONTHLY_NON_CASH_INCOME

val MONTHLY_INCOME_FIELDS: List<DeclarationFormField> =
    listOf(
        DeclarationFormField.MONTHLY_CASH_REGISTER_INCOME,
        DeclarationFormField.MONTHLY_POS_INCOME,
        DeclarationFormField.MONTHLY_NON_CASH_INCOME,
        DeclarationFormField.MONTHLY_OTHER_INCOME
    )
