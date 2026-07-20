plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.play.publisher) apply false
    alias(libs.plugins.sonarqube)
}

sonar {
    properties {
        property(
            "sonar.projectKey",
            providers.environmentVariable("SONAR_PROJECT_KEY").orElse(rootProject.name).get()
        )
        property("sonar.projectName", rootProject.name)
        property("sonar.sourceEncoding", "UTF-8")
        property("sonar.androidVariant", "debug")
        property("sonar.exclusions", "app/src/debug/**")
        property("sonar.coverage.exclusions", "app/src/debug/**")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            listOf(
                project(":app").layout.buildDirectory.file("reports/jacoco/jacocoDebugUnitTestReport/jacocoDebugUnitTestReport.xml").get().asFile,
                project(":app").layout.buildDirectory.file("reports/coverage/androidTest/debug/connected/report.xml").get().asFile
            ).joinToString(",")
        )

        providers.environmentVariable("SONAR_HOST_URL").orNull
            ?.takeIf(String::isNotBlank)
            ?.let { property("sonar.host.url", it) }
        providers.environmentVariable("SONAR_TOKEN").orNull
            ?.takeIf(String::isNotBlank)
            ?.let { property("sonar.token", it) }
    }
}

tasks.named("sonar") {
    dependsOn(":app:jacocoDebugUnitTestReport")
}

project(":app").tasks.matching { it.name == "sonarResolver" }.configureEach {
    // Sonar resolves androidTest assets, including Room's generated schema assets.
    dependsOn("copyRoomSchemasToAndroidTestAssetsDebugAndroidTest")
}
