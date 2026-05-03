package com.queukat.sbsgeorgia.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.SourceCategoryPresets
import java.util.Locale

private data class SourceCategoryMapping(val rawValue: String, @field:StringRes val labelResId: Int)

private val knownSourceCategories =
    listOf(
        SourceCategoryMapping(
            SourceCategoryPresets.SOFTWARE_SERVICES,
            R.string.source_category_software_services
        ),
        SourceCategoryMapping(
            SourceCategoryPresets.CONSULTING,
            R.string.source_category_consulting
        ),
        SourceCategoryMapping(
            SourceCategoryPresets.MARKETPLACE_PAYOUT,
            R.string.source_category_marketplace_payout
        ),
        SourceCategoryMapping(SourceCategoryPresets.OTHER, R.string.source_category_other),
        SourceCategoryMapping(SourceCategoryPresets.BANK_FEE, R.string.source_category_bank_fee),
        SourceCategoryMapping(
            SourceCategoryPresets.OWN_ACCOUNT_TRANSFER,
            R.string.source_category_own_account_transfer
        ),
        SourceCategoryMapping(
            SourceCategoryPresets.TAX_PAYMENT,
            R.string.source_category_tax_payment
        ),
        SourceCategoryMapping(
            SourceCategoryPresets.IMPORTED_STATEMENT_INCOME,
            R.string.source_category_imported_statement_income
        ),
        SourceCategoryMapping(
            SourceCategoryPresets.IMPORTED_STATEMENT_REVIEW,
            R.string.source_category_imported_statement_review
        )
    )

fun displaySourceCategory(context: Context, rawValue: String): String = knownSourceCategories
    .firstOrNull { it.rawValue.equals(rawValue.trim(), ignoreCase = true) }
    ?.let { context.getString(it.labelResId) }
    ?: rawValue

fun canonicalSourceCategory(context: Context, value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return trimmed
    val normalizedInput = trimmed.lowercase(Locale.ROOT)
    return knownSourceCategories
        .firstOrNull { mapping ->
            normalizedInput == mapping.rawValue.lowercase(Locale.ROOT) ||
                normalizedInput == context.getString(mapping.labelResId).lowercase(Locale.ROOT)
        }?.rawValue ?: trimmed
}

@Composable
fun sourceCategoryLabel(rawValue: String): String = displaySourceCategory(LocalContext.current, rawValue)
