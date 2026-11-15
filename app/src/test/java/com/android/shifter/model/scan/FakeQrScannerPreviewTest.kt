// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the fake's [FakeQrScanner.Preview], which needs Compose. */
@RunWith(AndroidJUnit4::class)
class FakeQrScannerPreviewTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun preview_rendersAtTheGivenSizeWithoutACamera() {
    val scanner = FakeQrScanner()
    composeTestRule.setContent { scanner.Preview(Modifier.size(200.dp).testTag(PREVIEW)) }

    composeTestRule
        .onNodeWithTag(PREVIEW)
        .assertWidthIsEqualTo(200.dp)
        .assertHeightIsEqualTo(200.dp)
  }

  @Test
  fun preview_shownAgain_clearsTheError() {
    val scanner = FakeQrScanner()
    var shown by mutableStateOf(true)
    composeTestRule.setContent { if (shown) scanner.Preview() }

    scanner.fail(ScanError.CAMERA_FAILED)
    shown = false
    composeTestRule.waitForIdle()
    assertEquals(ScanError.CAMERA_FAILED, scanner.error.value)

    shown = true
    composeTestRule.waitForIdle()
    assertNull(scanner.error.value)
  }

  private companion object {
    const val PREVIEW = "preview"
  }
}
