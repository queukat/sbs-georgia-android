package com.queukat.sbsgeorgia.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.room.InvalidationTracker
import com.queukat.sbsgeorgia.MainActivity
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.data.local.SbsGeorgiaDatabase
import com.queukat.sbsgeorgia.domain.model.DashboardSummary
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.usecase.ObserveDashboardSummaryUseCase
import com.queukat.sbsgeorgia.ui.common.formatAmount
import com.queukat.sbsgeorgia.ui.common.formatIsoDate
import com.queukat.sbsgeorgia.ui.common.formatMonthYear
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DashboardOverviewWidgetProvider : BaseHomeWidgetProvider(WidgetKind.OVERVIEW)

class DuePeriodWidgetProvider : BaseHomeWidgetProvider(WidgetKind.DUE_PERIOD)

class FocusWidgetProvider : BaseHomeWidgetProvider(WidgetKind.FOCUS)

fun requestHomeWidgetsUpdate(context: Context) {
    HomeWidgetUpdater.requestUpdateAll(context)
}

abstract class BaseHomeWidgetProvider(
    private val widgetKind: WidgetKind,
) : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        HomeWidgetUpdater.launchUpdate {
            try {
                HomeWidgetUpdater.update(
                    context = context,
                    appWidgetManager = appWidgetManager,
                    appWidgetIds = appWidgetIds,
                    widgetKind = widgetKind,
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}

@Singleton
class HomeWidgetRefreshObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: SbsGeorgiaDatabase,
) {
    @Volatile
    private var started = false

    private val observer = object : InvalidationTracker.Observer(
        "taxpayer_profile",
        "small_business_status_config",
        "reminder_config",
        "income_entry",
        "monthly_declaration_record",
    ) {
        override fun onInvalidated(tables: Set<String>) {
            HomeWidgetUpdater.requestUpdateAll(context)
        }
    }

    fun ensureStarted() {
        if (started) return
        synchronized(this) {
            if (started) return
            database.invalidationTracker.addObserver(observer)
            started = true
        }
    }
}

enum class WidgetKind {
    OVERVIEW,
    DUE_PERIOD,
    FOCUS,
}

private object HomeWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun requestUpdateAll(context: Context) {
        launchUpdate {
            updateAll(context.applicationContext)
        }
    }

    fun launchUpdate(block: suspend () -> Unit) {
        scope.launch {
            block()
        }
    }

    suspend fun update(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        widgetKind: WidgetKind,
    ) {
        if (appWidgetIds.isEmpty()) return

        val summary = loadDashboardSummary(context)
        appWidgetIds.forEach { appWidgetId ->
            val remoteViews = when (widgetKind) {
                WidgetKind.OVERVIEW -> buildOverviewRemoteViews(context, summary)
                WidgetKind.DUE_PERIOD -> buildDuePeriodRemoteViews(context, summary)
                WidgetKind.FOCUS -> buildFocusRemoteViews(context, summary)
            }
            appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
        }
    }

    private suspend fun updateAll(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        updateForProvider<DashboardOverviewWidgetProvider>(context, appWidgetManager, WidgetKind.OVERVIEW)
        updateForProvider<DuePeriodWidgetProvider>(context, appWidgetManager, WidgetKind.DUE_PERIOD)
        updateForProvider<FocusWidgetProvider>(context, appWidgetManager, WidgetKind.FOCUS)
    }

    private suspend inline fun <reified T : AppWidgetProvider> updateForProvider(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetKind: WidgetKind,
    ) {
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, T::class.java))
        update(context, appWidgetManager, appWidgetIds, widgetKind)
    }

    private suspend fun loadDashboardSummary(context: Context): DashboardSummary {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            HomeWidgetEntryPoint::class.java,
        )
        return entryPoint.observeDashboardSummaryUseCase().invoke().first()
    }

    private fun buildOverviewRemoteViews(
        context: Context,
        summary: DashboardSummary,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_overview).apply {
        setTextViewText(R.id.widget_title, context.getString(R.string.widget_overview_title))
        setTextViewText(R.id.widget_primary_value, formatAmount(summary.ytdIncomeGel, "GEL"))
        setTextViewText(R.id.widget_primary_label, context.getString(R.string.home_ytd_income))
        setTextViewText(R.id.widget_secondary_value, summary.unsettledMonthsCount.toString())
        setTextViewText(R.id.widget_secondary_label, context.getString(R.string.home_unsettled_months))
        setTextViewText(R.id.widget_tertiary_value, summary.unresolvedFxCount.toString())
        setTextViewText(R.id.widget_tertiary_label, context.getString(R.string.home_unresolved_fx_entries))
        val footer = if (summary.setupComplete) {
            context.getString(R.string.widget_paid_tax_value, formatAmount(summary.paidTaxAmountGel, "GEL"))
        } else {
            context.getString(R.string.widget_setup_required)
        }
        setTextViewText(R.id.widget_footer, footer)
        setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
    }

    private fun buildDuePeriodRemoteViews(
        context: Context,
        summary: DashboardSummary,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_due_period).apply {
        setTextViewText(R.id.widget_title, context.getString(R.string.widget_due_period_title))
        val duePeriod = summary.currentDuePeriod
        if (duePeriod == null) {
            setTextViewText(R.id.widget_month, context.getString(R.string.widget_no_due_period))
            setTextViewText(R.id.widget_status, "")
            setTextViewText(R.id.widget_due_date, "")
            setTextViewText(R.id.widget_amount, "")
            setTextViewText(R.id.widget_note, "")
        } else {
            bindDuePeriod(context, duePeriod)
        }
        setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
    }

    private fun RemoteViews.bindDuePeriod(
        context: Context,
        snapshot: MonthlyDeclarationSnapshot,
    ) {
        setTextViewText(R.id.widget_month, snapshot.period.incomeMonth.formatMonthYear())
        setTextViewText(
            R.id.widget_status,
            context.getString(
                R.string.widget_due_period_status_value,
                workflowStatusLabel(context, snapshot.workflowStatus),
            ),
        )
        val amountLabel = snapshot.estimatedTaxAmountGel?.let { amount ->
            context.getString(R.string.widget_due_period_tax_value, formatAmount(amount, "GEL"))
        } ?: context.getString(R.string.widget_due_period_tax_pending)
        setTextViewText(R.id.widget_amount, amountLabel)
        setTextViewText(
            R.id.widget_due_date,
            context.getString(R.string.widget_due_period_due_value, snapshot.period.filingWindow.dueDate.formatIsoDate()),
        )
        setTextViewText(
            R.id.widget_note,
            when {
                snapshot.setupRequired -> context.getString(R.string.widget_setup_required)
                snapshot.reviewNeeded -> context.getString(R.string.widget_review_needed)
                snapshot.unresolvedFxCount > 0 ->
                    context.getString(R.string.widget_unresolved_fx_value, snapshot.unresolvedFxCount)
                snapshot.zeroDeclarationSuggested ->
                    context.getString(R.string.widget_zero_declaration)
                else -> context.getString(R.string.widget_due_period_ready)
            },
        )
    }

    private fun buildFocusRemoteViews(
        context: Context,
        summary: DashboardSummary,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_focus).apply {
        setTextViewText(R.id.widget_title, context.getString(R.string.widget_focus_title))
        setTextViewText(R.id.widget_primary_value, summary.unsettledMonthsCount.toString())
        setTextViewText(R.id.widget_primary_label, context.getString(R.string.home_unsettled_months))
        setTextViewText(R.id.widget_secondary_value, summary.unresolvedFxCount.toString())
        setTextViewText(R.id.widget_secondary_label, context.getString(R.string.home_unresolved_fx_entries))
        val reminder = summary.nextReminderDay?.let {
            context.getString(R.string.widget_next_reminder_value, it)
        } ?: context.getString(R.string.widget_no_reminder)
        setTextViewText(R.id.widget_footer, reminder)
        setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
    }

    private fun openAppPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            7001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun workflowStatusLabel(
        context: Context,
        status: MonthlyWorkflowStatus,
    ): String = context.getString(
        when (status) {
            MonthlyWorkflowStatus.DRAFT -> R.string.workflow_status_draft
            MonthlyWorkflowStatus.READY_TO_FILE -> R.string.workflow_status_ready_to_file
            MonthlyWorkflowStatus.FILED -> R.string.workflow_status_filed
            MonthlyWorkflowStatus.TAX_PAYMENT_PENDING -> R.string.workflow_status_tax_payment_pending
            MonthlyWorkflowStatus.PAYMENT_SENT -> R.string.workflow_status_payment_sent
            MonthlyWorkflowStatus.PAYMENT_CREDITED -> R.string.workflow_status_payment_credited
            MonthlyWorkflowStatus.SETTLED -> R.string.workflow_status_settled
            MonthlyWorkflowStatus.OVERDUE -> R.string.workflow_status_overdue
        },
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface HomeWidgetEntryPoint {
    fun observeDashboardSummaryUseCase(): ObserveDashboardSummaryUseCase
}
