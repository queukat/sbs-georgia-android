import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val coverageRequested = providers.gradleProperty("coverage").map(String::toBoolean).getOrElse(false)
val unitTestCoverageRequested =
    coverageRequested ||
        gradle.startParameter.taskNames.any { taskName ->
            taskName.contains("jacoco", ignoreCase = true) || taskName.endsWith("sonar", ignoreCase = true)
        }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.detekt)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.play.publisher)
    jacoco
}

val releaseSigningProperties =
    Properties().apply {
        val signingFile = rootProject.file("keystore.properties")
        if (signingFile.exists()) {
            signingFile.inputStream().use(::load)
        }
    }
val playKeyFileProvider = providers.environmentVariable("PLAY_KEY_FILE")
val requiresOfficialReleaseSigning = gradle.startParameter.taskNames.any(
    ::requiresOfficialReleaseSigning
)

if (releaseSigningProperties.isEmpty() && requiresOfficialReleaseSigning) {
    throw GradleException("Release signing config is missing. Provide keystore.properties.")
}

android {
    namespace = "com.queukat.sbsgeorgia"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.queukat.sbsgeorgia"
        minSdk = 24
        targetSdk = 36
        versionCode = 11
        versionName = "1.0.10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(releaseSigningProperties.getProperty("storeFile"))
                storePassword = releaseSigningProperties.getProperty("storePassword")
                keyAlias = releaseSigningProperties.getProperty("keyAlias")
                keyPassword = releaseSigningProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            enableAndroidTestCoverage = coverageRequested
            enableUnitTestCoverage = unitTestCoverageRequested
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("benchmarkRelease") {
            signingConfig = signingConfigs.getByName("debug")
        }
        create("nonMinifiedRelease") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
        }
    }
}

play {
    defaultToAppBundles.set(true)
    track.set("internal")
    releaseStatus.set(ReleaseStatus.COMPLETED)

    playKeyFileProvider.orNull
        ?.takeIf { it.isNotBlank() }
        ?.let { serviceAccountCredentials.set(file(it)) }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

hilt {
    enableAggregatingTask = true
}

jacoco {
    toolVersion = "0.8.13"
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    parallel = true
    basePath.set(rootProject.projectDir)
}

ktlint {
    android.set(true)
    outputToConsole.set(true)
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget.set("17")
}

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    jvmTarget.set("17")
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class.java) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val qaLintTasks =
    setOf(
        "lintVitalAnalyzeNonMinifiedRelease",
        "lintVitalReportNonMinifiedRelease",
        "lintVitalNonMinifiedRelease"
    )

tasks.matching { it.name in qaLintTasks }.configureEach {
    enabled = false
}

tasks.register("installQa") {
    group = "install"
    description = "Installs the signed, non-minified release-like build for device UX testing."
    dependsOn("installNonMinifiedRelease")
}

val jacocoGeneratedClassExcludes = listOf(
    "**/R.class",
    "**/R\$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*_Factory.*",
    "**/*_MembersInjector.*",
    "**/*_Impl.*",
    "**/*Dao_Impl.*",
    "**/*Database_Impl.*",
    "**/*Hilt*.*",
    "**/*Dagger*.*"
)

tasks.register<JacocoReport>("jacocoDebugUnitTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(
        files(
            fileTree(layout.buildDirectory.dir("intermediates/classes/debug/transformDebugClassesWithAsm/dirs")) {
                exclude(jacocoGeneratedClassExcludes)
            }
        )
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
            )
        }
    )
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.google.material)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.pdfbox.android)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

private fun requiresOfficialReleaseSigning(taskName: String): Boolean {
    val simpleTaskName = taskName.substringAfterLast(':')
    if (simpleTaskName.contains("benchmarkRelease", ignoreCase = true)) return false
    if (simpleTaskName.contains("nonMinifiedRelease", ignoreCase = true)) return false

    val touchesReleaseArtifact =
        simpleTaskName.contains("assemble", ignoreCase = true) ||
            simpleTaskName.contains("bundle", ignoreCase = true) ||
            simpleTaskName.contains("package", ignoreCase = true) ||
            simpleTaskName.contains("publish", ignoreCase = true)

    return touchesReleaseArtifact && simpleTaskName.contains("Release", ignoreCase = true)
}
