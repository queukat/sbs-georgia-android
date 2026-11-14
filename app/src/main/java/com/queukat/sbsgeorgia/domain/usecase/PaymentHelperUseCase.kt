package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PaymentHelperData(
    val incomeMonth: YearMonth,
    val registrationId: String?,
    val taxpayerName: String?,
    val treasuryCode: String,
    val comment: String,
    val estimatedTaxAmountGel: BigDecimal,
    val snapshot: MonthlyDeclarationSnapshot?,
    val copyBundle: DeclarationCopyBundle?
)

class ObservePaymentHelperUseCase
@Inject
constructor(private val observeMonthDetailUseCase: ObserveMonthDetailUseCase) {
    operator fun invoke(yearMonth: YearMonth): Flow<PaymentHelperData> =
        observeMonthDetailUseCase.full(yearMonth).map { detail ->
            val snapshot = detail.snapshot
            val registrationId = detail.registrationId
            PaymentHelperData(
                incomeMonth = yearMonth,
                registrationId = registrationId,
                taxpayerName = detail.taxpayerName,
                treasuryCode = TREASURY_CODE,
                comment = buildPaymentComment(registrationId, yearMonth),
                estimatedTaxAmountGel = snapshot?.estimatedTaxAmountGel ?: BigDecimal.ZERO.setScale(2),
                snapshot = snapshot,
                copyBundle =
                buildDeclarationCopyBundle(
                    snapshot = snapshot,
                    registrationId = registrationId,
                    yearMonth = yearMonth,
                    formConfig = detail.formConfig
                )
            )
        }
}
