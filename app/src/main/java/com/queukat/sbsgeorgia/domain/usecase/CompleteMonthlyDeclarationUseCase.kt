package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationRecord
import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.model.MonthlyWorkflowStatus
import com.queukat.sbsgeorgia.domain.repository.MonthlyDeclarationRepository
import com.queukat.sbsgeorgia.domain.service.MonthlyCompletionPolicy
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

class CompleteMonthlyDeclarationUseCase
@Inject
constructor(
    private val repository: MonthlyDeclarationRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(snapshot: MonthlyDeclarationSnapshot) {
        val today = LocalDate.now(clock)
        val current = snapshot.record
        val paymentRequired = MonthlyCompletionPolicy.paymentRequired(snapshot.estimatedTaxAmountGel)

        repository.upsert(
            MonthlyDeclarationRecord(
                yearMonth = snapshot.period.incomeMonth,
                workflowStatus =
                if (paymentRequired) {
                    MonthlyWorkflowStatus.SETTLED
                } else {
                    MonthlyWorkflowStatus.FILED
                },
                zeroDeclarationPrepared =
                snapshot.zeroDeclarationPrepared || snapshot.zeroDeclarationSuggested,
                declarationFiledDate = current?.declarationFiledDate ?: today,
                paymentSentDate =
                if (paymentRequired) current?.paymentSentDate ?: today else null,
                paymentCreditedDate =
                if (paymentRequired) current?.paymentCreditedDate ?: today else null,
                paymentAmountGel =
                if (paymentRequired) {
                    current?.paymentAmountGel
                        ?: snapshot.estimatedTaxAmountGel
                        ?: BigDecimal.ZERO.setScale(2)
                } else {
                    null
                },
                notes = current?.notes.orEmpty()
            )
        )
    }
}
