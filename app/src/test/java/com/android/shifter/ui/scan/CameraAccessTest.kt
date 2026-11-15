// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraAccessTest {

  @Test
  fun cameraAccess_whenGranted_isGrantedWhateverTheDialog() {
    assertEquals(CameraAccess.GRANTED, cameraAccess(true, requested = false, false))
    assertEquals(CameraAccess.GRANTED, cameraAccess(true, requested = true, false))
  }

  @Test
  fun cameraAccess_beforeTheFirstRequest_isNotRequested() {
    assertEquals(CameraAccess.NOT_REQUESTED, cameraAccess(false, requested = false, false))
  }

  @Test
  fun cameraAccess_whenRefusedOnce_isDenied() {
    assertEquals(
        CameraAccess.DENIED,
        cameraAccess(false, requested = true, shouldShowRationale = true),
    )
  }

  @Test
  fun cameraAccess_whenRefusedWithoutRationale_isPermanentlyDenied() {
    assertEquals(
        CameraAccess.PERMANENTLY_DENIED,
        cameraAccess(false, requested = true, shouldShowRationale = false),
    )
  }
}
