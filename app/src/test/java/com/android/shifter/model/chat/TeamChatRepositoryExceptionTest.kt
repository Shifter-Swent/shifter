// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import org.junit.Assert.assertSame
import org.junit.Test

class TeamChatRepositoryExceptionTest {

  @Test
  fun everyException_keepsTheBackendErrorAsItsCause() {
    val backendError = RuntimeException("PERMISSION_DENIED")

    // The backend's error is what tells why a call failed in the logs: dropping it would leave
    // only the generic message.
    for (exception in
        listOf(
            TeamChatRepositoryException.NotAllowed(backendError),
            TeamChatRepositoryException.Unavailable(backendError),
            TeamChatRepositoryException.Unknown(backendError),
        )) {
      assertSame(exception::class.simpleName, backendError, exception.cause)
    }
  }
}
