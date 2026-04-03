package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.domain.model.MonthlyDeclarationSnapshot
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import java.math.BigDecimal
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class PaymentHelperData(
    val incomeMonth: YearMonth,
    val registrationId: String?,
    val taxpayerName: String?,
    val treasuryCode: String,
    val comment: String,
    val estimatedTaxAmountGel: BigDecimal,
    val snapshot: MonthlyDeclarationSnapshot?,
    val copyBundle: DeclarationCopyBundle?,
)

class ObservePaymentHelperUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val observeMonthDetailUseCase: ObserveMonthDetailUseCase,
) {
    operator fun invoke(yearMonth: YearMonth): Flow<PaymentHelperData> =
        combine(
            settingsRepository.observeTaxpayerProfile(),
            observeMonthDetailUseCase(yearMonth),
        ) { profile, monthDetail ->
            val snapshot = monthDetail.first
            val registrationId = profile?.registrationId
            val comment = buildPaymentComment(registrationId, yearMonth)
            PaymentHelperData(
                incomeMonth = yearMonth,
                registrationId = registrationId,
                taxpayerName = profile?.displayName,
                treasuryCode = TREASURY_CODE,
                comment = comment,
                estimatedTaxAmountGel = snapshot?.estimatedTaxAmountGel ?: BigDecimal.ZERO.setScale(2),
                snapshot = snapshot,
                copyBundle = buildDeclarationCopyBundle(snapshot, registrationId, yearMonth),
            )
        }
}
