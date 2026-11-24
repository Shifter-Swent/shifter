// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model

import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers what [RepositoryProvider] builds itself from the repositories it is given. */
class RepositoryProviderTest {

  @Test
  fun notificationPreferences_byDefault_readTheChoicesOfTheProfile() = runTest {
    val users = mockk<UserRepository>()
    every { users.observeDisabledNotifications(UID) } returns
        flowOf(setOf(NotificationKind.ANNOUNCEMENT))
    val provider =
        RepositoryProvider(
            mockk(),
            mockk(),
            users,
            mockk(),
            mockk(),
            mockk(),
            mockk(),
            mockk(),
            mockk(),
        )

    assertEquals(
        setOf(NotificationKind.ANNOUNCEMENT),
        provider.notificationPreferences.disabledKinds(UID).first(),
    )
  }

  private companion object {
    const val UID = "user-1"
  }
}
