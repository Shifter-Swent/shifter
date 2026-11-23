// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

import org.junit.Assert.assertSame
import org.junit.Test

class ShiftRepositoryExceptionTest {

  @Test
  fun everyException_keepsTheBackendErrorAsItsCause() {
    val backendError = RuntimeException("PERMISSION_DENIED")

    // The backend's error is what tells why a call failed in the logs: dropping it would leave
    // only the generic message.
    for (exception in
        listOf(
            ShiftRepositoryException.PermissionDenied(backendError),
            ShiftRepositoryException.Unavailable(backendError),
            ShiftRepositoryException.Unknown(backendError),
        )) {
      assertSame(exception::class.simpleName, backendError, exception.cause)
    }
  }
}
