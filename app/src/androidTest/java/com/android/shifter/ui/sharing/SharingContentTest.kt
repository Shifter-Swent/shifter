// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.sharing

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
import com.swent.shifter.ui.event.EventCreatedError
import com.swent.shifter.ui.event.EventCreatedUiState
import com.swent.shifter.ui.event.FakeClipboard
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharingContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val loaded =
      EventCreatedUiState(eventTitle = "City Marathon 2025", joinCode = "ABC234", isLoading = false)

  private val clipboard = FakeClipboard()

  /** What the share button was called with, as (event title, join code). */
  private val shared = mutableListOf<Pair<String, String>>()

  private fun setContent(
      state: EventCreatedUiState,
      onExport: () -> Unit = {},
      onRetry: () -> Unit = {},
  ) {
    val sharer = EventSharer { eventTitle, joinCode -> shared.add(eventTitle to joinCode) }
    composeTestRule.setContent {
      ShifterTheme {
        CompositionLocalProvider(LocalClipboard provides clipboard) {
          SharingContent(state, onShare = sharer::share, onExport = onExport, onRetry = onRetry)
        }
      }
    }
  }

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  private fun string(id: Int) =
      InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

  @Test
  fun loaded_showsTheQrCodeAndTheCode() {
    setContent(loaded)

    node(SharingScreenTestTags.QR_CODE).assertIsDisplayed()
    node(SharingScreenTestTags.JOIN_CODE).assertTextEquals("ABC234")
    composeTestRule.onNodeWithText(string(R.string.sharing_hint)).assertIsDisplayed()
  }

  @Test
  fun copy_putsTheCodeInTheClipboardAndConfirmsIt() {
    setContent(loaded)

    node(SharingScreenTestTags.COPY_BUTTON).performScrollTo().performClick()

    composeTestRule.waitUntil { clipboard.text != null }
    assertEquals("ABC234", clipboard.text)
    composeTestRule.onNodeWithText(string(R.string.event_join_code_copied)).assertIsDisplayed()
  }

  @Test
  fun share_sendsTheEventAndItsCode() {
    setContent(loaded)

    node(SharingScreenTestTags.SHARE_BUTTON).performScrollTo().performClick()

    assertEquals(listOf("City Marathon 2025" to "ABC234"), shared)
  }

  @Test
  fun export_isOfferedOnceTheCodeIsLoaded() {
    var exported = false
    setContent(loaded, onExport = { exported = true })

    node(SharingScreenTestTags.EXPORT_BUTTON).performScrollTo().assertIsEnabled().performClick()

    assertTrue(exported)
  }

  @Test
  fun loading_showsTheSpinnerAndOffersNothingToShare() {
    setContent(EventCreatedUiState())

    node(SharingScreenTestTags.LOADING_INDICATOR).assertIsDisplayed()
    node(SharingScreenTestTags.QR_CODE).assertDoesNotExist()
    node(SharingScreenTestTags.COPY_BUTTON).assertIsNotEnabled()
    node(SharingScreenTestTags.SHARE_BUTTON).performScrollTo().assertIsNotEnabled()
    node(SharingScreenTestTags.EXPORT_BUTTON).performScrollTo().assertIsNotEnabled()
  }

  @Test
  fun error_offersToRetry() {
    var retried = false
    setContent(
        EventCreatedUiState(isLoading = false, error = EventCreatedError.OFFLINE),
        onRetry = { retried = true },
    )

    node(SharingScreenTestTags.QR_CODE).assertDoesNotExist()
    node(SharingScreenTestTags.UNAVAILABLE).assertIsDisplayed()
    node(SharingScreenTestTags.SHARE_BUTTON).performScrollTo().assertIsNotEnabled()
    node(SharingScreenTestTags.RETRY_BUTTON).performScrollTo().performClick()

    assertTrue(retried)
  }
}
