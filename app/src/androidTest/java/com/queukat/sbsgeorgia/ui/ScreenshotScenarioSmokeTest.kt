package com.queukat.sbsgeorgia.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.queukat.sbsgeorgia.testing.PlayScreenshotContent
import com.queukat.sbsgeorgia.testing.PlayScreenshotScenario
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenshotScenarioSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun skipOnTvDevices() {
        assumePhoneLikeComposeTestDevice()
    }

    @Test
    fun playStoreScreenshotScenariosRender() {
        var currentScenario by mutableStateOf(PlayScreenshotScenario.OnboardingRegistry)

        composeRule.setContent {
            PlayScreenshotContent(scenario = currentScenario, localeTag = "en-US")
        }

        PlayScreenshotScenario.entries.forEach { scenario ->
            composeRule.runOnIdle {
                currentScenario = scenario
            }

            composeRule
                .onNodeWithTag("screenshot-ready-${scenario.id}")
                .assertIsDisplayed()
        }
    }
}
