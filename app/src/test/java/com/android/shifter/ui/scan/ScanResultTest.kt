// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import com.swent.shifter.ui.event.JoinEventError
import com.swent.shifter.ui.event.JoinEventUiState
import com.swent.shifter.ui.events.testEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What the scan shows of the join screen's lookup. */
class ScanResultTest {

  @Test
  fun toScanResult_whileLookingUp_isLooking() {
    val state = JoinEventUiState(joinCode = CODE, isLoading = true)

    assertEquals(ScanResult.Looking(CODE), state.toScanResult())
  }

  @Test
  fun toScanResult_withTheEventFound_isFound() {
    val event = testEvent()
    val state = JoinEventUiState(joinCode = CODE, event = event)

    assertEquals(ScanResult.Found(CODE, event), state.toScanResult())
  }

  @Test
  fun toScanResult_withAnError_isFailed() {
    val state = JoinEventUiState(joinCode = CODE, error = JoinEventError.UNKNOWN_CODE)

    assertEquals(ScanResult.Failed(JoinEventError.UNKNOWN_CODE), state.toScanResult())
  }

  @Test
  fun toScanResult_beforeAnyLookup_isNull() {
    assertNull(JoinEventUiState(joinCode = CODE).toScanResult())
  }

  private companion object {
    const val CODE = "ABC234"
  }
}
