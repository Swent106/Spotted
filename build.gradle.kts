// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // Sonar version comes from libs.versions.toml, so root and app can never disagree.
    alias(libs.plugins.sonar)
    id("com.google.gms.google-services") version "4.5.0" apply false
}

sonar {
    properties {
        property("sonar.projectKey", "Swent106_Spotted")
        property("sonar.organization", "swent106")
        property("sonar.host.url", "https://sonarcloud.io")
    }
}