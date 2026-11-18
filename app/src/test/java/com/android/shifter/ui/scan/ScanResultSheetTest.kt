// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import android.Manifest
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.scan.FakeQrScanner
import com.swent.shifter.ui.event.JoinEventError
import com.swent.shifter.ui.events.testEvent
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The result of the code read, in the scan screen with a fake camera, its permission granted as in
 * `ScanScreenTest`. The test plays the join screen: it sets the result and drops it to scan again.
 */
@RunWith(AndroidJUnit4::class)
class ScanResultSheetTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var result by mutableStateOf<ScanResult?>(null)
  private val scanned = mutableListOf<String>()
  private var continueClicks = 0

  /** The touch feedback the screen played, in order. */
  private val felt = mutableListOf<HapticFeedbackType>()
  private val haptics =
      object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
          felt += hapticFeedbackType
        }
      }

  private fun show(): FakeQrScanner {
    val scanner = FakeQrScanner()
    compose.setContent {
      val context = LocalContext.current
      val granting = remember {
        object : ContextWrapper(context) {
          override fun checkPermission(permission: String, pid: Int, uid: Int) =
              if (permission == Manifest.permission.CAMERA) PackageManager.PERMISSION_GRANTED
              else PackageManager.PERMISSION_DENIED
        }
      }
      CompositionLocalProvider(
          LocalContext provides granting,
          LocalHapticFeedback provides haptics,
      ) {
        ShifterTheme {
          ScanScreen(
              scanner = scanner,
              onCodeScanned = { scanned += it },
              onEnterCode = {},
              onBack = {},
              result = result,
              onContinue = { continueClicks++ },
              onScanAgain = { result = null },
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

  private fun showResult(shown: ScanResult) {
    result = shown
    compose.waitForIdle()
  }

  private fun tag(tag: String) = compose.onNodeWithTag(tag)

  private fun text(text: String) = compose.onNodeWithText(text)

  @Test
  fun lookingUp_showsTheCodeAndASpinner() {
    show()

    showResult(ScanResult.Looking(CODE))

    text(CODE).assertIsDisplayed()
    compose.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertIsDisplayed()
    tag(ScanScreenTestTags.CONTINUE).assertDoesNotExist()
  }

  @Test
  fun eventFound_showsTheEventAndSaysTheCodeWasRead() {
    show()

    showResult(ScanResult.Found(CODE, EVENT))

    tag(ScanScreenTestTags.RESULT_SHEET).assertIsDisplayed()
    text(CODE).assertIsDisplayed()
    text(EVENT.title).assertIsDisplayed()
    compose.onNodeWithText(EVENT.location.address, substring = true).assertIsDisplayed()
    tag(ScanScreenTestTags.HINT).assertTextEquals(CODE_READ)
  }

  @Test
  fun continue_goesOnWithTheEvent() {
    show()
    showResult(ScanResult.Found(CODE, EVENT))

    tag(ScanScreenTestTags.CONTINUE).performClick()

    assertEquals(1, continueClicks)
  }

  @Test
  fun unknownCode_saysNoEventUsesItAndOffersToScanAgain() {
    show()

    showResult(ScanResult.Failed(JoinEventError.UNKNOWN_CODE))

    text(NO_EVENT).assertIsDisplayed()
    text(UNKNOWN_CODE_HELP).assertIsDisplayed()
    tag(ScanScreenTestTags.RETRY).assertIsDisplayed()
    tag(ScanScreenTestTags.INVALID_CODE).assertIsDisplayed()
  }

  @Test
  fun lookupFailedOffline_saysSoAndKeepsTheCodeRead() {
    show()

    showResult(ScanResult.Failed(JoinEventError.OFFLINE))

    text(OFFLINE).assertIsDisplayed()
    text(UNKNOWN_CODE_HELP).assertDoesNotExist()
    tag(ScanScreenTestTags.HINT).assertTextEquals(CODE_READ)
  }

  @Test
  fun lookupFailed_rejectsByTouch() {
    val scanner = show()
    scanner.read(CODE)

    showResult(ScanResult.Failed(JoinEventError.UNKNOWN_CODE))

    assertEquals(listOf(HapticFeedbackType.Confirm, HapticFeedbackType.Reject), felt)
  }

  @Test
  fun codeRead_whileAResultShows_isIgnored() {
    val scanner = show()
    scanner.read(CODE)
    showResult(ScanResult.Looking(CODE))

    scanner.read(OTHER_CODE)

    assertEquals(listOf(CODE), scanned)
  }

  @Test
  fun scanAgain_hidesTheResultAndResumesTheScan() {
    val scanner = show()
    scanner.read(CODE)
    showResult(ScanResult.Failed(JoinEventError.UNKNOWN_CODE))

    tag(ScanScreenTestTags.RETRY).performClick()
    compose.waitForIdle()

    tag(ScanScreenTestTags.RESULT_SHEET).assertDoesNotExist()
    tag(ScanScreenTestTags.HINT).assertTextEquals(HINT)
    scanner.read(OTHER_CODE)
    assertEquals(listOf(CODE, OTHER_CODE), scanned)
  }

  private companion object {
    const val CODE = "ABC234"
    const val OTHER_CODE = "XYZ789"
    val EVENT = testEvent()
    const val HINT = "Point your camera at the event's QR code"
    const val CODE_READ = "Code read"
    const val NO_EVENT = "No event uses this code"
    const val UNKNOWN_CODE_HELP = "Ask the organizer for the event's QR code, or type its code."
    const val OFFLINE = "You are offline, try again later"
  }
}
