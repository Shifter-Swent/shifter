import java.io.IOException
import java.net.URI
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.kotlinCompose)
  alias(libs.plugins.ktfmt)
  alias(libs.plugins.sonar)
  alias(libs.plugins.kotlinSerialization)
  id("com.google.gms.google-services")
  id("jacoco")
}

android {
  namespace = "com.swent.shifter"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.swent.shifter"
    minSdk = 28
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables { useSupportLibrary = true }

    // The Maps key lives in local.properties (never committed). Without it the map stays grey,
    // but the app still builds and runs, e.g. on CI.
    val localProperties = Properties()
    rootProject
        .file("local.properties")
        .takeIf { it.exists() }
        ?.inputStream()
        ?.use { localProperties.load(it) }
    manifestPlaceholders["MAPS_API_KEY"] =
        localProperties.getProperty("MAPS_API_KEY") ?: "DEFAULT_API_KEY"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
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

  testCoverage { jacocoVersion = "0.8.11" }

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

    java.directories.clear()
    java.directories.addAll(test.java.directories)
    res.directories.clear()
    res.directories.addAll(test.res.directories)
    resources.directories.clear()
    resources.directories.addAll(test.resources.directories)
  }

  sourceSets.getByName("test") {
    java.directories.clear()
    res.directories.clear()
    resources.directories.clear()
  }
}

// With AGP 9+ the JVM target is set outside the Android block.
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

/**
 * Downloads the app's typeface, Satoshi, from Fontshare into a generated resource folder
 * (`font/satoshi_variable.ttf`).
 *
 * Its licence (ITF Free Font License) lets the app embed the font, but forbids sharing the file
 * through a repository, so the file is never committed: every build gets its own copy from
 * Fontshare. Gradle keeps it in `build/` until the next clean.
 */
abstract class DownloadFontTask : DefaultTask() {
  @get:Input abstract val url: Property<String>

  /** The path of the font file inside the zip that Fontshare serves. */
  @get:Input abstract val entry: Property<String>

  /** The name of the font resource, `R.font.<name>`. */
  @get:Input abstract val resourceName: Property<String>

  @get:OutputDirectory abstract val outputDir: DirectoryProperty

  @TaskAction
  fun download() {
    val target = outputDir.get().dir("font").file("${resourceName.get()}.ttf").asFile
    target.parentFile.mkdirs()
    var found = false
    try {
      val connection = URI(url.get()).toURL().openConnection()
      connection.connectTimeout = TIMEOUT_MILLIS
      connection.readTimeout = TIMEOUT_MILLIS
      ZipInputStream(connection.getInputStream()).use { zip ->
        var zipEntry: ZipEntry? = zip.nextEntry
        while (zipEntry != null && !found) {
          if (zipEntry.name == entry.get()) {
            target.outputStream().use { out -> zip.copyTo(out) }
            found = true
          }
          zipEntry = zip.nextEntry
        }
      }
    } catch (e: IOException) {
      throw GradleException(
          "Could not download the Satoshi font from ${url.get()} (${e.message}). " +
              "The first build needs a network connection.",
          e,
      )
    }
    if (!found) throw GradleException("${entry.get()} is missing from ${url.get()}.")
  }

  private companion object {
    /** How long to wait for Fontshare to connect, then for each read, so a stuck server fails. */
    const val TIMEOUT_MILLIS = 30_000
  }
}

val downloadSatoshiFont =
    tasks.register<DownloadFontTask>("downloadSatoshiFont") {
      group = "build"
      description = "Downloads the Satoshi typeface from Fontshare into the generated resources."
      url.set("https://api.fontshare.com/v2/fonts/download/satoshi")
      entry.set("Satoshi_Complete/Fonts/TTF/Satoshi-Variable.ttf")
      resourceName.set("satoshi_variable")
      outputDir.set(layout.buildDirectory.dir("generated/res/satoshi"))
    }

androidComponents {
  onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(
        downloadSatoshiFont,
        DownloadFontTask::outputDir,
    )
  }
}

sonar {
  properties {
    property("sonar.projectKey", "Shifter-Swent_shifter")
    property("sonar.projectName", "shifter")
    property("sonar.organization", "shifter-swent")
    property("sonar.host.url", "https://sonarcloud.io")
    property("sonar.gradle.skipCompile", "true")
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
    // Compose previews are tooling: the IDE renders them and no test runs them, so they would
    // only ever count as uncovered. They live in their own *Previews.kt files to be left out here.
    property("sonar.coverage.exclusions", "**/*Previews.kt")
    // Paths to JaCoCo XML coverage report files.
    property(
        "sonar.coverage.jacoco.xmlReportPaths",
        "${project.layout.buildDirectory.get()}/reports/jacoco/jacocoTestReport/jacocoTestReport.xml",
    )
  }
}

// When a library is used both by robolectric and connected tests, use this function
fun DependencyHandlerScope.globalTestImplementation(dep: Any) {
  androidTestImplementation(dep)
  testImplementation(dep)
}

// Kaspresso pulls in espresso-contrib -> accessibility-test-framework, which still depends on the
// long-deprecated protobuf-lite 3.0.1. Its GeneratedMessageLite shadows the protobuf runtime that
// firebase-firestore needs, so any instrumented test writing a document would fail with
// NoSuchMethodError. Firestore ships its own protobuf runtime, so dropping the stale one is enough.
configurations.named("androidTestImplementation") {
  exclude(mapOf("group" to "com.google.protobuf", "module" to "protobuf-lite"))
}

dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.firestore)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.maps.compose)
  implementation(libs.play.services.maps)
  implementation(libs.play.services.location)
  // QR codes of the join codes, generated on the device
  implementation(libs.zxing.core)
  // The camera that reads the QR codes of the join codes
  implementation(libs.camera.core)
  implementation(libs.camera.camera2)
  implementation(libs.camera.lifecycle)
  implementation(libs.camera.view)
  implementation(libs.camera.mlkit.vision)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.compose.navigation)
  implementation(libs.compose.icons.core)
  implementation(libs.compose.icons.extended)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  testImplementation(libs.androidx.lifecycle.runtime.testing)
  implementation(platform(libs.compose.bom))
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  // The target of a notification, written as JSON in the intent its tap opens (#612)
  implementation(libs.kotlinx.serialization.json)
  globalTestImplementation(libs.androidx.junit)
  globalTestImplementation(libs.androidx.espresso.core)

  // Credential Manager (for Google Sign-In)
  implementation(libs.credentials)
  implementation(libs.credentials.play.services.auth)
  implementation(libs.googleid)

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

  // --------- Kaspresso test framework ----------
  globalTestImplementation(libs.kaspresso)
  globalTestImplementation(libs.kaspresso.compose)

  // ----------       Robolectric     ------------
  testImplementation(libs.robolectric)
  testImplementation(libs.mockk)

  // ----------       Coroutines      ------------
  globalTestImplementation(libs.kotlinx.coroutines.test)
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

  val debugTree =
      fileTree(
          "${project.layout.buildDirectory.get()}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
      ) {
        exclude(fileFilter)
      }

  val mainSrc = "${project.layout.projectDirectory}/src/main/java"
  sourceDirectories.setFrom(files(mainSrc))
  classDirectories.setFrom(files(debugTree))
  executionData.setFrom(
      fileTree(project.layout.buildDirectory.get()) {
        include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        include("outputs/code_coverage/debugAndroidTest/connected/*/coverage.ec")
      }
  )
}
