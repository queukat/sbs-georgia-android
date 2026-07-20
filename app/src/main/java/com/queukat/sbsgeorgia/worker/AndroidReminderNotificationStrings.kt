package com.queukat.sbsgeorgia.worker

import android.content.Context
import com.queukat.sbsgeorgia.R
import com.queukat.sbsgeorgia.domain.service.ReminderNotificationMessage
import com.queukat.sbsgeorgia.domain.service.ReminderNotificationStrings
import com.queukat.sbsgeorgia.domain.service.ReminderType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidReminderNotificationStrings
@Inject
constructor(@param:ApplicationContext private val context: Context) :
    ReminderNotificationStrings {
    override fun title(type: ReminderType): String = context.getString(
        when (type) {
            ReminderType.DECLARATION -> R.string.notification_declaration_reminder_title
            ReminderType.PAYMENT -> R.string.notification_payment_reminder_title
        }
    )

    override fun body(
        message: ReminderNotificationMessage,
        incomeMonth: YearMonth,
        unresolvedFxCount: Int,
        dueDate: LocalDate
    ): String {
        val monthReference = formatMonthReference(incomeMonth)
        val dueDateLabel = formatDueDate(dueDate)
        val fxEntryCount =
            context.resources.getQuantityString(
                R.plurals.notification_fx_entry_count,
                unresolvedFxCount,
                unresolvedFxCount
            )
        return when (message) {
            ReminderNotificationMessage.DECLARATION_REVIEW_AND_FX ->
                context.getString(
                    R.string.notification_declaration_review_fx_body,
                    monthReference,
                    fxEntryCount,
                    dueDateLabel
                )
            ReminderNotificationMessage.DECLARATION_FX ->
                context.getString(
                    R.string.notification_declaration_fx_body,
                    fxEntryCount,
                    monthReference,
                    dueDateLabel
                )
            ReminderNotificationMessage.DECLARATION_REVIEW ->
                context.getString(
                    R.string.notification_declaration_review_body,
                    monthReference,
                    dueDateLabel
                )
            ReminderNotificationMessage.DECLARATION_ZERO_PREPARED ->
                context.getString(
                    R.string.notification_declaration_zero_prepared_body,
                    monthReference,
                    dueDateLabel
                )
            ReminderNotificationMessage.DECLARATION_ZERO_SUGGESTED ->
                context.getString(
                    R.string.notification_declaration_zero_suggested_body,
                    monthReference,
                    dueDateLabel
                )
            ReminderNotificationMessage.DECLARATION_DEFAULT ->
                context.getString(
                    R.string.notification_declaration_default_body,
                    monthReference,
                    dueDateLabel
                )
            ReminderNotificationMessage.PAYMENT_REVIEW_AND_FX ->
                context.getString(
                    R.string.notification_payment_review_fx_body,
                    monthReference,
                    fxEntryCount,
                    dueDateLabel
                )
            ReminderNotificationMessage.PAYMENT_FX ->
                context.getString(
                    R.string.notification_payment_fx_body,
                    fxEntryCount,
                    monthReference,
                    dueDateLabel
                )
            ReminderNotificationMessage.PAYMENT_REVIEW ->
                context.getString(
                    R.string.notification_payment_review_body,
                    monthReference,
                    dueDateLabel
                )
            ReminderNotificationMessage.PAYMENT_FILED ->
                context.getString(
                    R.string.notification_payment_filed_body,
                    monthReference,
                    dueDateLabel
                )
            ReminderNotificationMessage.PAYMENT_PENDING ->
                context.getString(
                    R.string.notification_payment_pending_body,
                    monthReference,
                    dueDateLabel
                )
            ReminderNotificationMessage.PAYMENT_DEFAULT ->
                context.getString(
                    R.string.notification_payment_default_body,
                    monthReference,
                    dueDateLabel
                )
        }
    }

    private fun formatMonthReference(incomeMonth: YearMonth): String = incomeMonth.atDay(1).format(
        DateTimeFormatter.ofPattern("LLLL yyyy", currentLocale())
    )

    private fun formatDueDate(dueDate: LocalDate): String = dueDate.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale())
    )

    private fun currentLocale(): Locale {
        val locales = context.resources.configuration.locales
        return if (locales.size() > 0) locales.get(0) else Locale.getDefault()
    }
}
