package com.queukat.sbsgeorgia.testing

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.model.DashboardSummary
import com.queukat.sbsgeorgia.domain.model.DeclarationInclusion
import com.queukat.sbsgeorgia.domain.model.ExtractionConfidence
import com.queukat.sbsgeorgia.domain.model.FilingWindow
import com.queukat.sbsgeorgia.domain.model.FxRateSource
import com.queukat.sbsgeorgia.domain.model.IncomeEntry
import com.queukat.sbsgeorgia.domain.model.IncomeSourceType
import com.queukat.sbsgeorgia.domain.model.MonthlyCurrencyTotal
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationPeriod
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.domain.model.OnboardingPreviewNote
import com.queukat.sbsgeorgia.domain.model.ParsedDateField
import com.queukat.sbsgeorgia.domain.model.ParsedTextField
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.usecase.ChartPoint
import com.queukat.sbsgeorgia.domain.usecase.buildDeclarationCopyBundle
import com.queukat.sbsgeorgia.ui.charts.ChartsScreen
import com.queukat.sbsgeorgia.ui.charts.ChartsUiState
import com.queukat.sbsgeorgia.ui.home.HomeScreen
import com.queukat.sbsgeorgia.ui.home.HomeUiState
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementRowUiState
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementScreen
import com.queukat.sbsgeorgia.ui.importstatement.ImportStatementUiState
import com.queukat.sbsgeorgia.ui.monthdetails.MonthDetailScreen
import com.queukat.sbsgeorgia.ui.monthdetails.MonthDetailUiState
import com.queukat.sbsgeorgia.ui.months.MonthsMonthItemUiState
import com.queukat.sbsgeorgia.ui.months.MonthsScreen
import com.queukat.sbsgeorgia.ui.months.MonthsUiState
import com.queukat.sbsgeorgia.ui.months.MonthsYearSection
import com.queukat.sbsgeorgia.ui.onboarding.OnboardingScreen
import com.queukat.sbsgeorgia.ui.onboarding.OnboardingUiState
import com.queukat.sbsgeorgia.ui.common.sbsNavigationBarItemColors
import com.queukat.sbsgeorgia.ui.theme.SbsGeorgiaTheme
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

enum class PlayScreenshotScenario(
    val id: String,
    val fileName: String,
) {
    OnboardingRegistry(
        id = "onboarding-registry",
        fileName = "01-onboarding-registry-preview",
    ),
    HomeDashboard(
        id = "home-dashboard",
        fileName = "02-home-dashboard",
    ),
    MonthsOverview(
        id = "months-overview",
        fileName = "03-months-overview",
    ),
    MonthDetail(
        id = "month-detail",
        fileName = "04-month-detail-copy-tools",
    ),
    ImportPreview(
        id = "import-preview",
        fileName = "05-import-preview",
    ),
    Charts(
        id = "charts",
        fileName = "06-charts",
    ),
    ;

    companion object {
        fun fromId(id: String?): PlayScreenshotScenario =
            entries.firstOrNull { it.id == id } ?: HomeDashboard
    }
}

@Composable
fun PlayScreenshotContent(
    scenario: PlayScreenshotScenario,
    localeTag: String,
) {
    val baseContext = LocalContext.current
    val localizedContext = remember(baseContext, localeTag) {
        baseContext.withLocale(localeTag)
    }
    val localizedConfiguration = remember(localizedContext) {
        Configuration(localizedContext.resources.configuration)
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedConfiguration,
    ) {
        SbsGeorgiaTheme(themeMode = ThemeMode.LIGHT) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .testTag("screenshot-ready-${scenario.id}"),
            ) {
                when (scenario) {
                    PlayScreenshotScenario.OnboardingRegistry -> OnboardingRegistryScenario()
                    PlayScreenshotScenario.HomeDashboard -> HomeDashboardScenario()
                    PlayScreenshotScenario.MonthsOverview -> MonthsOverviewScenario()
                    PlayScreenshotScenario.MonthDetail -> MonthDetailScenario()
                    PlayScreenshotScenario.ImportPreview -> ImportPreviewScenario()
                    PlayScreenshotScenario.Charts -> ChartsScenario()
                }
            }
        }
    }
}

@Composable
private fun OnboardingRegistryScenario() {
    OnboardingScreen(
        innerPadding = PaddingValues(),
        uiState = OnboardingUiState(
            preview = OnboardingImportPreview(
                sourceFileName = "registry-extract.pdf",
                sourceFingerprint = "registry-demo",
                documentType = OnboardingDocumentType.REGISTRY_EXTRACT,
                displayName = ParsedTextField(
                    value = "Individual Entrepreneur Iaroslav Rychenkov",
                    confidence = ExtractionConfidence.CONFIDENT,
                ),
                legalForm = ParsedTextField(
                    value = "Individual Entrepreneur",
                    confidence = ExtractionConfidence.CONFIDENT,
                ),
                registrationId = ParsedTextField(
                    value = "306449082",
                    confidence = ExtractionConfidence.CONFIDENT,
                ),
                registrationDate = ParsedDateField(
                    value = LocalDate.of(2023, 11, 24),
                    confidence = ExtractionConfidence.CONFIDENT,
                ),
                legalAddress = ParsedTextField(
                    value = "Georgia, Tbilisi, Samgori District, Police Street I Dead End N5, Floor 2, N4a",
                    confidence = ExtractionConfidence.REVIEW_REQUIRED,
                ),
                notes = listOf(
                    OnboardingPreviewNote.REGISTRY_EFFECTIVE_DATE_MANUAL,
                    OnboardingPreviewNote.REVIEW_BEFORE_APPLY,
                ),
            ),
            displayName = "Individual Entrepreneur Iaroslav Rychenkov",
            legalForm = "Individual Entrepreneur",
            registrationId = "306449082",
            registrationDate = "2023-11-24",
            legalAddress = "Georgia, Tbilisi, Samgori District, Police Street I Dead End N5, Floor 2, N4a",
            effectiveDate = LocalDate.of(2026, 3, 7),
        ),
        onImportRegistryExtract = {},
        onImportCertificate = {},
        onApplyPreview = {},
        onDisplayNameChanged = {},
        onLegalFormChanged = {},
        onRegistrationIdChanged = {},
        onRegistrationDateChanged = {},
        onLegalAddressChanged = {},
        onActivityTypeChanged = {},
        onCertificateNumberChanged = {},
        onCertificateIssuedDateChanged = {},
        onEffectiveDateChanged = {},
        onTaxRatePercentChanged = {},
        onComplete = {},
    )
}

@Composable
private fun HomeDashboardScenario() {
    TopLevelScreenshotFrame(selectedIndex = 0) { innerPadding ->
        HomeScreen(
            innerPadding = innerPadding,
            uiState = HomeUiState(summary = sampleDashboardSummary()),
            onOpenMonths = {},
            onOpenCharts = {},
            onAddIncome = {},
            onImportStatement = {},
            onOpenSettings = {},
        )
    }
}

@Composable
private fun MonthsOverviewScenario() {
    TopLevelScreenshotFrame(selectedIndex = 1) { innerPadding ->
        MonthsScreen(
            innerPadding = innerPadding,
            uiState = MonthsUiState(
                sections = listOf(
                    MonthsYearSection(
                        year = 2026,
                        items = listOf(
                            MonthsMonthItemUiState(
                                snapshot = sampleSnapshot(
                                    month = YearMonth.of(2026, 3),
                                    graph20 = "8450.00",
                                    graph15 = "18450.00",
                                    tax = "84.50",
                                    originalTotal = "3000.00",
                                    workflowStatus = MonthlyWorkflowStatus.READY_TO_FILE,
                                ),
                                canQuickSettleMonth = true,
                                monthAlreadySettled = false,
                            ),
                            MonthsMonthItemUiState(
                                snapshot = sampleSnapshot(
                                    month = YearMonth.of(2026, 2),
                                    graph20 = "10000.00",
                                    graph15 = "10000.00",
                                    tax = "100.00",
                                    originalTotal = "3500.00",
                                    workflowStatus = MonthlyWorkflowStatus.FILED,
                                ),
                                canQuickSettleMonth = false,
                                monthAlreadySettled = true,
                            ),
                        ),
                    ),
                ),
            ),
            onMonthClick = {},
            onSettleMonth = {},
            onAddIncome = {},
            onImportStatement = {},
        )
    }
}

@Composable
private fun MonthDetailScenario() {
    val snapshot = sampleSnapshot(
        month = YearMonth.of(2026, 3),
        graph20 = "8450.00",
        graph15 = "18450.00",
        tax = "84.50",
        originalTotal = "3000.00",
        workflowStatus = MonthlyWorkflowStatus.READY_TO_FILE,
    )
    MonthDetailScreen(
        innerPadding = PaddingValues(),
        uiState = MonthDetailUiState(
            yearMonth = snapshot.period.incomeMonth,
            snapshot = snapshot,
            entries = listOf(
                IncomeEntry(
                    id = 1L,
                    sourceType = IncomeSourceType.IMPORTED_STATEMENT,
                    incomeDate = LocalDate.of(2026, 3, 12),
                    originalAmount = BigDecimal("3000.00"),
                    originalCurrency = "USD",
                    sourceCategory = "Software services",
                    note = "Invoice 2026-03",
                    declarationInclusion = DeclarationInclusion.INCLUDED,
                    gelEquivalent = BigDecimal("8450.00"),
                    rateSource = FxRateSource.OFFICIAL_NBG_JSON,
                    manualFxOverride = false,
                    createdAtEpochMillis = 1L,
                    updatedAtEpochMillis = 1L,
                ),
            ),
            copyBundle = buildDeclarationCopyBundle(
                snapshot = snapshot,
                registrationId = "306449082",
                yearMonth = snapshot.period.incomeMonth,
            ),
            isFilingWindowOpen = true,
        ),
        snackbarHostState = SnackbarHostState(),
        onBack = {},
        onAddIncome = {},
        onEditEntry = {},
        onOpenPaymentHelper = {},
        onOpenFxOverride = {},
        onOpenWorkflowStatus = {},
        onDeleteEntry = {},
        onResolveOfficialRates = {},
        onToggleZeroPrepared = {},
    )
}

@Composable
private fun ImportPreviewScenario() {
    ImportStatementScreen(
        innerPadding = PaddingValues(),
        uiState = ImportStatementUiState(
            sourceFileName = "statement-818670212_260402_120010.pdf",
            rows = listOf(
                ImportStatementRowUiState(
                    transactionFingerprint = "tx-1",
                    incomeDate = LocalDate.of(2026, 3, 15),
                    description = "FOR SOFTWARE SERVICES",
                    additionalInformation = "Invoice 001",
                    paidOutLabel = "0.00 USD",
                    paidInLabel = "1250.00 USD",
                    balanceLabel = "4280.15 USD",
                    suggestedInclusion = DeclarationInclusion.INCLUDED,
                    finalInclusion = DeclarationInclusion.INCLUDED,
                    amount = "1250.00",
                    currency = "USD",
                    sourceCategory = "Software services",
                    duplicate = false,
                ),
                ImportStatementRowUiState(
                    transactionFingerprint = "tx-2",
                    incomeDate = LocalDate.of(2026, 3, 17),
                    description = "Internal transfer",
                    additionalInformation = "Own account transfer",
                    paidOutLabel = "0.00 USD",
                    paidInLabel = "250.00 USD",
                    balanceLabel = "4530.15 USD",
                    suggestedInclusion = DeclarationInclusion.EXCLUDED,
                    finalInclusion = DeclarationInclusion.EXCLUDED,
                    amount = "250.00",
                    currency = "USD",
                    sourceCategory = "Own transfer",
                    duplicate = false,
                ),
            ),
            selectedIncomeCount = 1,
        ),
        snackbarHostState = SnackbarHostState(),
        onBack = {},
        onPickPdf = {},
        onIncludeAsTaxableChanged = { _, _ -> },
        onDateChanged = { _, _ -> },
        onAmountChanged = { _, _ -> },
        onCurrencyChanged = { _, _ -> },
        onSourceCategoryChanged = { _, _ -> },
        onImportApproved = {},
    )
}

@Composable
private fun ChartsScenario() {
    ChartsScreen(
        innerPadding = PaddingValues(),
        uiState = ChartsUiState(
            year = 2026,
            monthlyIncomePoints = listOf(
                ChartPoint("Jan", BigDecimal("4200.00")),
                ChartPoint("Feb", BigDecimal("5800.00")),
                ChartPoint("Mar", BigDecimal("8450.00")),
                ChartPoint("Apr", BigDecimal("6250.00")),
            ),
            cumulativePoints = listOf(
                ChartPoint("Jan", BigDecimal("4200.00")),
                ChartPoint("Feb", BigDecimal("10000.00")),
                ChartPoint("Mar", BigDecimal("18450.00")),
                ChartPoint("Apr", BigDecimal("24700.00")),
            ),
            ytdIncomeGel = BigDecimal("24700.00"),
            peakMonthLabel = "March 2026",
            unresolvedMonthsCount = 0,
        ),
        onBack = {},
    )
}

@Composable
private fun TopLevelScreenshotFrame(
    selectedIndex: Int,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val labels = listOf(
                    stringResource(R.string.nav_home),
                    stringResource(R.string.nav_months),
                    stringResource(R.string.nav_settings),
                )
                val icons = listOf(
                    Icons.Outlined.Home,
                    Icons.Outlined.CalendarMonth,
                    Icons.Outlined.Settings,
                )
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = {},
                        icon = { Icon(imageVector = icons[index], contentDescription = null) },
                        label = { Text(label) },
                        colors = sbsNavigationBarItemColors(),
                    )
                }
            }
        },
    ) { innerPadding ->
        content(innerPadding)
    }
}

private fun sampleDashboardSummary(): DashboardSummary {
    val currentDuePeriod = sampleSnapshot(
        month = YearMonth.of(2026, 3),
        graph20 = "8450.00",
        graph15 = "18450.00",
        tax = "84.50",
        originalTotal = "3000.00",
        workflowStatus = MonthlyWorkflowStatus.READY_TO_FILE,
    )
    return DashboardSummary(
        taxpayerName = "Iaroslav Rychenkov",
        registrationId = "306449082",
        setupComplete = true,
        ytdIncomeGel = BigDecimal("24700.00"),
        unresolvedFxCount = 0,
        unsettledMonthsCount = 1,
        paidTaxAmountGel = BigDecimal("184.50"),
        paymentMismatchMonthsCount = 0,
        currentDuePeriod = currentDuePeriod,
        nextReminderDay = 13,
    )
}

private fun sampleSnapshot(
    month: YearMonth,
    graph20: String,
    graph15: String,
    tax: String,
    originalTotal: String,
    workflowStatus: MonthlyWorkflowStatus,
): MonthlyDeclarationSnapshot = MonthlyDeclarationSnapshot(
    period = MonthlyDeclarationPeriod(
        incomeMonth = month,
        filingWindow = FilingWindow(
            start = month.plusMonths(1).atDay(1),
            endInclusive = month.plusMonths(1).atDay(15),
            dueDate = month.plusMonths(1).atDay(15),
        ),
        inScope = true,
        outOfScope = false,
    ),
    workflowStatus = workflowStatus,
    graph20TotalGel = BigDecimal(graph20),
    graph15CumulativeGel = BigDecimal(graph15),
    originalCurrencyTotals = listOf(MonthlyCurrencyTotal("USD", BigDecimal(originalTotal))),
    estimatedTaxAmountGel = BigDecimal(tax),
    unresolvedFxCount = 0,
    zeroDeclarationSuggested = false,
    zeroDeclarationPrepared = false,
    reviewNeeded = false,
    setupRequired = false,
    record = null,
)

private fun Context.withLocale(localeTag: String): Context {
    val locale = Locale.forLanguageTag(localeTag.ifBlank { "en-US" })
    Locale.setDefault(locale)
    val configuration = Configuration(resources.configuration)
    configuration.setLocales(LocaleList(locale))
    configuration.setLayoutDirection(locale)
    return createConfigurationContext(configuration)
}
