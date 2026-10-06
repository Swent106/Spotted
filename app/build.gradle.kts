plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlin.compose)
  // No Kotlin Android plugin: Kotlin is built into AGP 9.
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.sonar)
  id("jacoco")
  id("com.google.gms.google-services")
}

// Replaces android { kotlinOptions { } }, which no longer exists with AGP 9's built-in Kotlin.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

android {
  namespace = "com.android.spotted"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.android.spotted"
    minSdk = 28
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
          getDefaultProguardFile("proguard-android-optimize.txt"),
          "proguard-rules.pro",
      )
    }

    debug {
      enableUnitTestCoverage = true
      enableAndroidTestCoverage = true
    }
  }

  testCoverage { jacocoVersion = "0.8.13" }

  buildFeatures { compose = true }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }

  // Robolectric needs to be run only in debug. But its tests are placed in the shared source set
  // (test)
  // The next lines transfers the src/test/* from shared to the testDebug one
  //
  // This prevent errors from occurring during unit tests
  sourceSets.getByName("testDebug") {
    val test = sourceSets.getByName("test")

    java.directories.addAll(test.java.directories)
    kotlin.directories.addAll(test.kotlin.directories)
    res.directories.addAll(test.res.directories)
    resources.directories.addAll(test.resources.directories)
  }

  sourceSets.getByName("test") {
    java.directories.clear()
    kotlin.directories.clear()
    res.directories.clear()
    resources.directories.clear()
  }
}

configurations.configureEach { exclude(group = "com.google.protobuf", module = "protobuf-lite") }

sonar {
  properties {
    property("sonar.projectKey", "Swent106_Spotted")
    property("sonar.projectName", "Spotted")
    property("sonar.organization", "swent106")
    property("sonar.host.url", "https://sonarcloud.io")
    // Comma-separated paths to the various directories containing the *.xml JUnit report files.
    // Each path may be absolute or relative to the project base directory.
    property(
        "sonar.junit.reportPaths",
        "${project.layout.buildDirectory.get()}/test-results/testDebugUnitTest/",
    )
    // Paths to xml files with Android Lint issues. If the main flavor is changed, this file will
    // have to be changed too.
    property(
        "sonar.androidLint.reportPaths",
        "${project.layout.buildDirectory.get()}/reports/lint-results-debug.xml",
    )
    // Paths to JaCoCo XML coverage report files.
    property(
        "sonar.coverage.jacoco.xmlReportPaths",
        "${project.layout.buildDirectory.get()}/reports/jacoco/jacocoTestReport/jacocoTestReport.xml",
    )
    // Exclude images and debug-only configuration from the analysis
    property("sonar.exclusions", "**/*.webp, **/*.png, **/*.jpg, **/src/debug/**")
  }
}

// When a library is used both by robolectric and connected tests, use this function
fun DependencyHandlerScope.globalTestImplementation(dep: Any) {
  androidTestImplementation(dep)
  testImplementation(dep)
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.firebase.auth)
  implementation(libs.material)
  implementation(libs.androidx.lifecycle.runtime.ktx)

  // ------------- Firebase ------------------
  implementation(platform("com.google.firebase:firebase-bom:32.8.1"))
  implementation("com.google.firebase:firebase-analytics")
  implementation("com.google.firebase:firebase-firestore")

  // ------------- Images ------------------
  implementation(libs.coil.compose)

  // ------------- Jetpack Compose ------------------
  val composeBom = platform(libs.compose.bom)
  implementation(composeBom)
  globalTestImplementation(composeBom)

  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  // Material Design 3
  implementation(libs.compose.material3)
  // Integration with activities
  implementation(libs.compose.activity)
  // Integration with ViewModels
  implementation(libs.compose.viewmodel)
  // Android Studio Preview support
  implementation(libs.compose.preview)
  debugImplementation(libs.compose.tooling)
  // UI Tests
  globalTestImplementation(libs.compose.test.junit)
  debugImplementation(libs.compose.test.manifest)

  // ------------- Tests ------------------
  testImplementation(libs.junit)
  testImplementation("org.mockito:mockito-core:5.11.0") // test-only: must not ship in the app
  globalTestImplementation(libs.androidx.junit)
  globalTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.runner)
  // androidTestImplementation(libs.androidx.tracing)

  // --------- Kaspresso test framework ----------
  globalTestImplementation(libs.kaspresso)
  globalTestImplementation(libs.kaspresso.compose)

  // ----------       Robolectric     ------------
  testImplementation(libs.robolectric)
}

tasks.withType<Test> {
  // Configure Jacoco for each tests
  configure<JacocoTaskExtension> {
    isIncludeNoLocationClasses = true
    excludes = listOf("jdk.internal.*")
  }
}

tasks.register("jacocoTestReport", JacocoReport::class) {
  mustRunAfter("testDebugUnitTest", "connectedDebugAndroidTest")

  reports {
    xml.required = true
    html.required = true
  }

  val fileFilter =
      listOf(
          "**/R.class",
          "**/R$*.class",
          "**/BuildConfig.*",
          "**/Manifest*.*",
          "**/*Test*.*",
          "android/**/*.*",
      )

  // Compiled Kotlin classes moved with AGP 9's built-in Kotlin. Both the old and the new
  // locations are listed; only the one that exists after a clean build contributes.
  val classDirs =
      listOf(
              "tmp/kotlin-classes/debug",
              "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
              "intermediates/javac/debug/compileDebugJavaWithJavac/classes",
          )
          .map { dir -> fileTree(layout.buildDirectory.dir(dir)) { exclude(fileFilter) } }

  sourceDirectories.setFrom(
      files(
          "${project.layout.projectDirectory}/src/main/java",
          "${project.layout.projectDirectory}/src/main/kotlin",
      )
  )
  classDirectories.setFrom(classDirs)
  executionData.setFrom(
      fileTree(project.layout.buildDirectory.get()) {
        include("outputs/unit_test_code_coverage/debugUnitTest/*.exec")
        include("outputs/code_coverage/debugAndroidTest/connected/**/*.ec")
      }
  )
}
