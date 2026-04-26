package com.queukat.sbsgeorgia.domain.usecase

import com.queukat.sbsgeorgia.data.importer.StatementDocumentReader
import com.queukat.sbsgeorgia.data.importer.StatementTextExtractor
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.ReminderConfig
import com.queukat.sbsgeorgia.domain.model.SmallBusinessStatusConfig
import com.queukat.sbsgeorgia.domain.model.TaxpayerProfile
import com.queukat.sbsgeorgia.domain.model.ThemeMode
import com.queukat.sbsgeorgia.domain.repository.SettingsRepository
import com.queukat.sbsgeorgia.domain.service.onboarding.OnboardingDocumentParser
import com.queukat.sbsgeorgia.worker.ReminderScheduler
import java.time.LocalTime
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class LoadOnboardingDocumentPreviewUseCase @Inject constructor(
    private val statementDocumentReader: StatementDocumentReader,
    private val statementTextExtractor: StatementTextExtractor,
    private val onboardingDocumentParser: OnboardingDocumentParser,
) {
    suspend operator fun invoke(
        uriString: String,
        expectedDocumentType: OnboardingDocumentType,
    ) = statementDocumentReader.read(uriString).let { document ->
        onboardingDocumentParser.parse(
            sourceFileName = document.fileName,
            sourceFingerprint = document.sourceFingerprint,
            extractedText = statementTextExtractor.extractText(document.bytes),
            expectedDocumentType = expectedDocumentType,
        )
    }
}

class CompleteOnboardingUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val upsertSettingsUseCase: UpsertSettingsUseCase,
    private val reminderScheduler: ReminderScheduler,
) {
    suspend operator fun invoke(
        profile: TaxpayerProfile,
        config: SmallBusinessStatusConfig,
    ) {
        val reminders = settingsRepository.observeReminderConfig().first() ?: ReminderConfig(
            declarationReminderDays = listOf(10, 13, 15),
            paymentReminderDays = listOf(10, 13, 15),
            declarationRemindersEnabled = true,
            paymentRemindersEnabled = true,
            defaultReminderTime = LocalTime.of(9, 0),
            themeMode = ThemeMode.SYSTEM,
        )
        upsertSettingsUseCase(
            profile = profile,
            config = config,
            reminders = reminders,
        )
        reminderScheduler.reschedule(reminders)
    }
}
