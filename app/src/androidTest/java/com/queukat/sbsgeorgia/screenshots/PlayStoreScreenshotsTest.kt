package com.queukat.sbsgeorgia.screenshots

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.queukat.sbsgeorgia.testing.PlayScreenshotActivity
import com.queukat.sbsgeorgia.testing.PlayScreenshotScenario
import com.queukat.sbsgeorgia.ui.assumePhoneLikeComposeTestDevice
import java.io.File
import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayStoreScreenshotsTest {
    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        assumePhoneLikeComposeTestDevice()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun captureOnboardingRegistry() = capture(PlayScreenshotScenario.OnboardingRegistry)

    @Test
    fun captureHomeDashboard() = capture(PlayScreenshotScenario.HomeDashboard)

    @Test
    fun captureMonthsOverview() = capture(PlayScreenshotScenario.MonthsOverview)

    @Test
    fun captureMonthDetail() = capture(PlayScreenshotScenario.MonthDetail)

    @Test
    fun captureImportPreview() = capture(PlayScreenshotScenario.ImportPreview)

    @Test
    fun captureCharts() = capture(PlayScreenshotScenario.Charts)

    private fun capture(scenario: PlayScreenshotScenario) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            Intent(context, PlayScreenshotActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(PlayScreenshotActivity.EXTRA_SCENARIO_ID, scenario.id)
                putExtra(PlayScreenshotActivity.EXTRA_LOCALE_TAG, localeTag)
            }
        val scenarioHandle = ActivityScenario.launch<PlayScreenshotActivity>(intent)

        try {
            instrumentation.waitForIdleSync()
            device.waitForIdle()
            SystemClock.sleep(700)
            assertNoSystemCrashDialogs()

            val outputFile =
                File(outputDirectory(), "${scenario.fileName}.png").apply {
                    parentFile?.mkdirs()
                    delete()
                }
            assertTrue(
                "Failed to capture screenshot for ${scenario.id}",
                device.takeScreenshot(outputFile)
            )
            assertTrue("Screenshot file not created for ${scenario.id}", outputFile.exists())
            publishScreenshot(outputFile, scenario.fileName)
        } finally {
            scenarioHandle.close()
        }
    }

    private fun assertNoSystemCrashDialogs() {
        val knownCrashTexts =
            listOf(
                "keeps stopping",
                "isn't responding",
                "continues to stop",
                "has stopped"
            )
        val hasDialog =
            knownCrashTexts.any { text ->
                device.hasObject(By.textContains(text))
            }
        assertTrue("System crash/ANR dialog is visible; screenshot capture aborted.", !hasDialog)
    }

    private fun outputDirectory(): File {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return File(context.cacheDir, "screenshot-temp/$localeTag")
    }

    private fun publishScreenshot(sourceFile: File, displayNameWithoutExtension: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentResolver = context.contentResolver
            val collection = MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
            val relativePath =
                "${Environment.DIRECTORY_PICTURES}/SbsGeorgiaScreenshots/localized/$localeTag"
            val values =
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$displayNameWithoutExtension.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            val uri =
                contentResolver.insert(collection, values)
                    ?: error("Failed to create MediaStore entry for $displayNameWithoutExtension")
            writeMediaStoreFile(contentResolver, uri, sourceFile)
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } else {
            val publicDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val targetFile =
                File(
                    publicDir,
                    "SbsGeorgiaScreenshots/localized/$localeTag/$displayNameWithoutExtension.png"
                )
            targetFile.parentFile?.mkdirs()
            sourceFile.copyTo(targetFile, overwrite = true)
        }
    }

    private fun writeMediaStoreFile(contentResolver: android.content.ContentResolver, uri: Uri, sourceFile: File) {
        contentResolver.openOutputStream(uri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: throw IOException("Unable to open MediaStore output stream for $uri")
    }

    companion object {
        private val localeTag: String by lazy {
            InstrumentationRegistry
                .getArguments()
                .getString("testLocale")
                .orEmpty()
                .ifBlank { PlayScreenshotActivity.DEFAULT_LOCALE_TAG }
        }

        @BeforeClass
        @JvmStatic
        fun clearPreviousOutput() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val basePicturesDir =
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: return
            File(basePicturesDir, "SbsGeorgiaScreenshots/localized/$localeTag")
                .deleteRecursively()
        }
    }
}
