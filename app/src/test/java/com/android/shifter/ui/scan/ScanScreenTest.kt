// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.scan.FakeQrScanner
import com.swent.shifter.model.scan.ScanError
import com.swent.shifter.ui.navigation.NavigationTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The scan screen with a fake camera, its permission granted as in `ScanFallbackTest`. */
@RunWith(AndroidJUnit4::class)
class ScanScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val scanned = mutableListOf<String>()
  private var enterCodeClicks = 0
  private var backClicks = 0

  private fun show(scanner: FakeQrScanner = FakeQrScanner()): FakeQrScanner {
    compose.setContent {
      val context = LocalContext.current
      val granting = remember {
        object : ContextWrapper(context) {
          override fun checkPermission(permission: String, pid: Int, uid: Int) =
              if (permission == Manifest.permission.CAMERA) PackageManager.PERMISSION_GRANTED
              else PackageManager.PERMISSION_DENIED
        }
      }
      CompositionLocalProvider(LocalContext provides granting) {
        ShifterTheme {
          ScanScreen(
              scanner = scanner,
              onCodeScanned = { scanned += it },
              onEnterCode = { enterCodeClicks++ },
              onBack = { backClicks++ },
          )
        }
      }
    }
    // The codes are hot: one read before the screen listens would be lost.
    compose.waitUntil { scanner.activeCollectors > 0 }
    return scanner
  }

  private fun FakeQrScanner.read(text: String) {
    emit(text)
    compose.waitForIdle()
  }

  private fun tag(tag: String) = compose.onNodeWithTag(tag)

  @Test
  fun open_saysWhereToPointTheCamera() {
    show()

    tag(ScanScreenTestTags.HINT).assertIsDisplayed()
    compose.onNodeWithText("Scan a QR code").assertIsDisplayed()
    tag(ScanScreenTestTags.INVALID_CODE).assertDoesNotExist()
  }

  @Test
  fun scan_withAValidCode_reportsIt() {
    show().read(" ABC234\n")

    assertEquals(listOf("ABC234"), scanned)
    tag(ScanScreenTestTags.INVALID_CODE).assertDoesNotExist()
  }

  @Test
  fun scan_withALowerCaseCode_reportsItUpperCased() {
    show().read("abc234")

    assertEquals(listOf("ABC234"), scanned)
    tag(ScanScreenTestTags.INVALID_CODE).assertDoesNotExist()
  }

  @Test
  fun scan_withAnotherText_saysSoAndGoesOn() {
    val scanner = show()

    // Normalized, this URL would give HTTPSE, a valid code: it must be refused.
    scanner.read("https://example.com")
    tag(ScanScreenTestTags.INVALID_CODE).assertIsDisplayed()
    tag(ScanScreenTestTags.HINT).assertDoesNotExist()
    assertTrue(scanned.isEmpty())

    scanner.read("ABC234")
    assertEquals(listOf("ABC234"), scanned)
    tag(ScanScreenTestTags.INVALID_CODE).assertDoesNotExist()
    tag(ScanScreenTestTags.HINT).assertIsDisplayed()
  }

  @Test
  fun enterTheCodeInstead_goesBackToTheTypedCode() {
    show()

    tag(ScanScreenTestTags.ENTER_CODE).performClick()

    assertEquals(1, enterCodeClicks)
  }

  @Test
  fun back_leavesTheScreen() {
    show()

    tag(NavigationTestTags.GO_BACK_BUTTON).performClick()

    assertEquals(1, backClicks)
  }

  @Test
  fun torch_togglesTheCameraTorch() {
    val scanner = show()

    tag(ScanScreenTestTags.TORCH).assertIsOff().performClick()

    assertTrue(scanner.torchOn.value)
    tag(ScanScreenTestTags.TORCH).assertIsOn()
  }

  @Test
  fun withoutATorch_hidesItsButton() {
    show(FakeQrScanner(hasTorch = false))

    tag(ScanScreenTestTags.TORCH).assertDoesNotExist()
  }

  @Test
  fun cameraError_showsTheFallbackWithOnlyBack() {
    val scanner = show()

    scanner.fail(ScanError.NO_CAMERA)
    compose.waitForIdle()

    tag(ScanScreenTestTags.FALLBACK).assertIsDisplayed()
    tag(NavigationTestTags.GO_BACK_BUTTON).assertIsDisplayed()
    compose.onNodeWithText("Scan a QR code").assertDoesNotExist()
    tag(ScanScreenTestTags.TORCH).assertDoesNotExist()
  }
}
