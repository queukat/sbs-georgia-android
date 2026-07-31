package com.queukat.sbsgeorgia.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.usecase.ChartPoint
import com.queukat.sbsgeorgia.ui.charts.ChartsScreen
import com.queukat.sbsgeorgia.ui.charts.ChartsUiState
import com.queukat.sbsgeorgia.ui.common.formatAmount
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import java.math.BigDecimal
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun skipOnTvDevices() {
        assumePhoneLikeComposeTestDevice()
    }

    @Test
    fun chartsKeepExactValuesVisibleForMonthlyAndCumulativePoints() {
        val monthlyPointValue = BigDecimal("123.45")
        val cumulativePointValue = BigDecimal("789.01")

        composeRule.setContent {
            SbsGeorgiaTheme(themeMode = ThemeMode.SYSTEM) {
                ChartsScreen(
                    innerPadding = PaddingValues(),
                    uiState =
                    ChartsUiState(
                        year = 2026,
                        monthlyIncomePoints =
                        listOf(
                            ChartPoint(label = "JAN", value = monthlyPointValue),
                            ChartPoint(label = "FEB", value = BigDecimal("456.78"))
                        ),
                        cumulativePoints =
                        listOf(
                            ChartPoint(label = "JAN", value = cumulativePointValue),
                            ChartPoint(label = "FEB", value = BigDecimal("1245.79"))
                        )
                    ),
                    onYearSelected = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("charts-monthly-section").performScrollTo()
        composeRule
            .onNodeWithText(formatAmount(monthlyPointValue, "GEL"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("charts-cumulative-section").performScrollTo()
        composeRule
            .onNodeWithText(formatAmount(cumulativePointValue, "GEL"))
            .assertIsDisplayed()
    }
}
