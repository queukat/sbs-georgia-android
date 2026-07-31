package com.queukat.sbsgeorgia.domain.service

import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class MonthUserJourneyState {
    MONTH_UNAVAILABLE,
    SETUP_REQUIRED,
    OUT_OF_SCOPE,
    ADD_OR_IMPORT_INCOME,
    REVIEW_IMPORTED_ROWS,
    RESOLVE_FX,
    WAIT_FOR_FILING_WINDOW,
    COPY_DECLARATION_VALUES,
    FILE_DECLARATION_EXTERNALLY,
    PREPARE_PAYMENT,
    CONFIRM_PAYMENT_SENT,
    CONFIRM_PAYMENT_CREDITED,
    SETTLED
}

enum class MonthlyActionBlocker {
    SETUP_REQUIRED,
    OUT_OF_SCOPE,
    FILING_WINDOW_CLOSED,
    UNRESOLVED_FX,
    REVIEW_REQUIRED,
    MISSING_PAYMENT_COMMENT,
    NO_PAYMENT_DUE
}

data class MonthlyDeclarationActionState(
    val journeyState: MonthUserJourneyState,
    val blockers: List<MonthlyActionBlocker> = emptyList(),
    val filingWindowOpen: Boolean = false,
    val filingOpensOn: LocalDate? = null,
    val canCopyDeclarationValues: Boolean = false,
    val canCopyPaymentText: Boolean = false,
    val canPreparePayment: Boolean = false,
    val canQuickSettleMonth: Boolean = false,
    val monthAlreadySettled: Boolean = false,
    val paymentRequired: Boolean = false
)

@Singleton
class MonthlyDeclarationActionPlanner @Inject constructor(private val clock: Clock) {
    fun plan(
        snapshot: MonthlyDeclarationSnapshot?,
        registrationId: String? = null,
        referenceDate: LocalDate = LocalDate.now(clock)
    ): MonthlyDeclarationActionState {
        if (snapshot == null) {
            return MonthlyDeclarationActionState(
                journeyState = MonthUserJourneyState.MONTH_UNAVAILABLE
            )
        }

        val filingWindowOpen = !referenceDate.isBefore(snapshot.period.filingWindow.start)
        val baseStatus = snapshot.record?.workflowStatus ?: snapshot.workflowStatus
        val monthAlreadySettled = MonthlyCompletionPolicy.isComplete(snapshot)
        val positiveTaxDue = MonthlyCompletionPolicy.paymentRequired(snapshot.estimatedTaxAmountGel)
        val blockers =
            buildList {
                if (snapshot.setupRequired) add(MonthlyActionBlocker.SETUP_REQUIRED)
                if (snapshot.period.outOfScope) add(MonthlyActionBlocker.OUT_OF_SCOPE)
                if (!snapshot.period.outOfScope && !filingWindowOpen) {
                    add(MonthlyActionBlocker.FILING_WINDOW_CLOSED)
                }
                if (snapshot.unresolvedFxCount > 0) add(MonthlyActionBlocker.UNRESOLVED_FX)
                if (snapshot.reviewNeeded && snapshot.unresolvedFxCount == 0) {
                    add(MonthlyActionBlocker.REVIEW_REQUIRED)
                }
                if (positiveTaxDue && registrationId.isNullOrBlank()) {
                    add(MonthlyActionBlocker.MISSING_PAYMENT_COMMENT)
                }
                if (!positiveTaxDue) add(MonthlyActionBlocker.NO_PAYMENT_DUE)
            }

        val hardBlockers =
            setOf(
                MonthlyActionBlocker.SETUP_REQUIRED,
                MonthlyActionBlocker.OUT_OF_SCOPE,
                MonthlyActionBlocker.FILING_WINDOW_CLOSED,
                MonthlyActionBlocker.UNRESOLVED_FX,
                MonthlyActionBlocker.REVIEW_REQUIRED
            )
        val hasDeclarationBlockers = blockers.any { it in hardBlockers }
        val canCopyDeclarationValues = !hasDeclarationBlockers
        val canCopyPaymentText =
            canCopyDeclarationValues &&
                positiveTaxDue &&
                !registrationId.isNullOrBlank()
        val canPreparePayment =
            canCopyDeclarationValues &&
                positiveTaxDue &&
                baseStatus in paymentPreparationStatuses &&
                !registrationId.isNullOrBlank()
        val canQuickSettleMonth =
            canCopyDeclarationValues &&
                !monthAlreadySettled

        return MonthlyDeclarationActionState(
            journeyState = journeyState(
                snapshot = snapshot,
                baseStatus = baseStatus,
                blockers = blockers,
                positiveTaxDue = positiveTaxDue
            ),
            blockers = blockers,
            filingWindowOpen = filingWindowOpen,
            filingOpensOn = if (!snapshot.period.outOfScope && !filingWindowOpen) {
                snapshot.period.filingWindow.start
            } else {
                null
            },
            canCopyDeclarationValues = canCopyDeclarationValues,
            canCopyPaymentText = canCopyPaymentText,
            canPreparePayment = canPreparePayment,
            canQuickSettleMonth = canQuickSettleMonth,
            monthAlreadySettled = monthAlreadySettled,
            paymentRequired = positiveTaxDue
        )
    }

    private fun journeyState(
        snapshot: MonthlyDeclarationSnapshot,
        baseStatus: MonthlyWorkflowStatus,
        blockers: List<MonthlyActionBlocker>,
        positiveTaxDue: Boolean
    ): MonthUserJourneyState = when {
        MonthlyActionBlocker.SETUP_REQUIRED in blockers -> MonthUserJourneyState.SETUP_REQUIRED
        MonthlyActionBlocker.OUT_OF_SCOPE in blockers -> MonthUserJourneyState.OUT_OF_SCOPE
        snapshot.graph20TotalGel.signum() == 0 &&
            !snapshot.zeroDeclarationSuggested &&
            !snapshot.zeroDeclarationPrepared ->
            MonthUserJourneyState.ADD_OR_IMPORT_INCOME
        MonthlyActionBlocker.UNRESOLVED_FX in blockers -> MonthUserJourneyState.RESOLVE_FX
        MonthlyActionBlocker.REVIEW_REQUIRED in blockers -> MonthUserJourneyState.REVIEW_IMPORTED_ROWS
        MonthlyActionBlocker.FILING_WINDOW_CLOSED in blockers -> MonthUserJourneyState.WAIT_FOR_FILING_WINDOW
        WorkflowStatusPolicy.isFullySettled(baseStatus) -> MonthUserJourneyState.SETTLED
        baseStatus == MonthlyWorkflowStatus.PAYMENT_SENT -> MonthUserJourneyState.CONFIRM_PAYMENT_CREDITED
        baseStatus in paymentPreparationStatuses && positiveTaxDue -> MonthUserJourneyState.PREPARE_PAYMENT
        baseStatus in paymentPreparationStatuses -> MonthUserJourneyState.SETTLED
        baseStatus == MonthlyWorkflowStatus.READY_TO_FILE -> MonthUserJourneyState.FILE_DECLARATION_EXTERNALLY
        else -> MonthUserJourneyState.COPY_DECLARATION_VALUES
    }

    private companion object {
        val paymentPreparationStatuses =
            setOf(
                MonthlyWorkflowStatus.FILED,
                MonthlyWorkflowStatus.TAX_PAYMENT_PENDING
            )
    }
}
