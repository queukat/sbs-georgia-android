package com.queukat.sbsgeorgia.domain.model

object SourceCategoryPresets {
    const val SOFTWARE_SERVICES = "Software services"
    const val CONSULTING = "Consulting"
    const val MARKETPLACE_PAYOUT = "Marketplace payout"
    const val OTHER = "Other"
    const val BANK_FEE = "Bank fee"
    const val OWN_ACCOUNT_TRANSFER = "Own account transfer"
    const val TAX_PAYMENT = "Tax payment"
    const val IMPORTED_STATEMENT_INCOME = "Imported statement income"
    const val IMPORTED_STATEMENT_REVIEW = "Imported statement review"

    val manualSuggestions = listOf(
        SOFTWARE_SERVICES,
        CONSULTING,
        MARKETPLACE_PAYOUT,
        OTHER,
    )
}
