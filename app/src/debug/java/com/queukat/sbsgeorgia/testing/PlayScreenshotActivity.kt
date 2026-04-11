package com.queukat.sbsgeorgia.testing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class PlayScreenshotActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scenario = PlayScreenshotScenario.fromId(intent.getStringExtra(EXTRA_SCENARIO_ID))
        val localeTag = intent.getStringExtra(EXTRA_LOCALE_TAG).orEmpty().ifBlank { DEFAULT_LOCALE_TAG }

        setContent {
            PlayScreenshotContent(
                scenario = scenario,
                localeTag = localeTag,
            )
        }
    }

    companion object {
        const val EXTRA_SCENARIO_ID = "scenario_id"
        const val EXTRA_LOCALE_TAG = "locale_tag"
        const val DEFAULT_LOCALE_TAG = "en-US"
    }
}
