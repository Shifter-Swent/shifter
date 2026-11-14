// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the fake the scan screens are tested with, so they see the codes a test gives. */
@RunWith(AndroidJUnit4::class)
class FakeQrScannerTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun codes_emitsTheCodesInOrder() = runTest {
    val scanner = FakeQrScanner()
    val received = mutableListOf<String>()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { scanner.codes.toList(received) }

    scanner.emit("AB23CD")
    scanner.emit("EF45GH")

    assertEquals(listOf("AB23CD", "EF45GH"), received)
    job.cancel()
  }

  @Test
  fun codes_givenBeforeCollection_areDelivered() = runTest {
    val scanner = FakeQrScanner()

    scanner.emit("AB23CD")
    scanner.emit("AB23CD")

    assertEquals(listOf("AB23CD", "AB23CD"), scanner.codes.take(2).toList())
  }

  @Test
  fun errors_emitsTheErrors() = runTest {
    val scanner = FakeQrScanner()

    scanner.fail(ScanError.NO_CAMERA)

    assertEquals(ScanError.NO_CAMERA, scanner.errors.first())
  }

  @Test
  fun torch_recordsTheLastState() {
    val scanner = FakeQrScanner()
    assertFalse(scanner.torchOn)

    scanner.torch(true)
    assertTrue(scanner.torchOn)

    scanner.torch(false)
    assertFalse(scanner.torchOn)
  }

  @Test
  fun preview_rendersWithoutACamera() {
    val scanner = FakeQrScanner()
    composeTestRule.setContent { scanner.Preview(Modifier.size(200.dp).testTag(PREVIEW)) }

    composeTestRule
        .onNodeWithTag(PREVIEW)
        .assertWidthIsEqualTo(200.dp)
        .assertHeightIsEqualTo(200.dp)
  }

  private companion object {
    const val PREVIEW = "preview"
  }
}
