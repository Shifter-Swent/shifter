// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.authentication

import android.os.Looper
import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.authentication.FakeAuthRepository
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.UserRepositoryException
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class StartViewModelTest {

  private val session = FakeAuthRepository(AuthUser(uid = UID, email = null, displayName = null))

  private fun stateRead(users: UserRepository, auth: FakeAuthRepository = session): StartState {
    val viewModel = StartViewModel(auth, users)
    shadowOf(Looper.getMainLooper()).idle()
    return viewModel.state.value
  }

  @Test
  fun mode_savedOnTheProfile_isTheOneEntered() {
    assertEquals(
        StartState.Enter(UserMode.ORGANIZER),
        stateRead(FakeUserRepository(listOf(user(UserMode.ORGANIZER)))),
    )
  }

  @Test
  fun mode_noModeOrUnreadable_entersVolunteerWithoutWriting() {
    val withoutMode = FakeUserRepository(listOf(user(mode = null)))
    val unreadable = FakeUserRepository().apply { failure = UserRepositoryException.Unavailable() }

    assertEquals(StartState.Enter(UserMode.VOLUNTEER), stateRead(withoutMode))
    assertNull(withoutMode.users.getValue(UID).mode)
    assertEquals(StartState.Enter(UserMode.VOLUNTEER), stateRead(unreadable))
  }

  @Test
  fun mode_noProfile_asksForTheModeWithoutWriting() {
    val users = FakeUserRepository()

    assertEquals(StartState.ChooseMode, stateRead(users))
    assertTrue(users.users.isEmpty())
  }

  @Test
  fun mode_profileNotAnswering_entersVolunteerAfterTheTimeout() {
    val users = mockk<UserRepository>()
    coEvery { users.getUser(UID) } coAnswers { awaitCancellation() }
    val viewModel = StartViewModel(session, users)

    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(StartState.Reading, viewModel.state.value)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(StartViewModel.READ_TIMEOUT_MILLIS))

    assertEquals(StartState.Enter(UserMode.VOLUNTEER), viewModel.state.value)
  }

  @Test
  fun mode_withoutSession_isNeverRead() {
    assertEquals(
        StartState.Reading,
        stateRead(FakeUserRepository(listOf(user(UserMode.ORGANIZER))), FakeAuthRepository()),
    )
  }

  private fun user(mode: UserMode?) =
      User(uid = UID, displayName = "Ada", email = "", createdAt = Instant.EPOCH, mode = mode)

  private companion object {
    const val UID = "uid-1"
  }
}
