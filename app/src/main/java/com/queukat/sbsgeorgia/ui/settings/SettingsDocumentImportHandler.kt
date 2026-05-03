package com.queukat.sbsgeorgia.ui.settings

import android.net.Uri
import com.queukat.sbsgeorgia.domain.model.OnboardingDocumentType
import com.queukat.sbsgeorgia.domain.model.OnboardingImportPreview
import com.queukat.sbsgeorgia.ui.common.document.DocumentImportAction
import com.queukat.sbsgeorgia.ui.common.document.DocumentImportLoadResult
import com.queukat.sbsgeorgia.ui.common.document.DocumentImportStrings
import com.queukat.sbsgeorgia.ui.common.document.loadDocumentImportPreview

internal class SettingsDocumentImportHandler(
    private val strings: DocumentImportStrings,
    private val loadPreview: suspend (String, OnboardingDocumentType) -> OnboardingImportPreview
) {
    val previewAppliedMessage: String
        get() = strings.previewApplied

    suspend fun load(uri: Uri, action: DocumentImportAction): DocumentImportLoadResult = loadDocumentImportPreview(
        uriString = uri.toString(),
        action = action,
        strings = strings,
        loadPreview = loadPreview
    )
}
