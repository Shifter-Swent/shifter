// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.R
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.model.RepositoryProviderFactory
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.PublicProfile
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.UserRepositoryException
import com.swent.shifter.ui.theme.ShifterTheme
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests [SettingsRoute] with the production ViewModel, built by its factory as `ShifterNavHost`
 * builds it, from repositories wired to the emulators, so signing out really clears a Firebase
 * session.
 */
@RunWith(AndroidJUnit4::class)
class SettingsRouteTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val repositories =
      RepositoryProviderFactory.create(FirestoreEmulator.firestore, AuthEmulator.auth)
  private val repository = repositories.authRepository
  private val credentialManager = RecordingCredentialManager()
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  @Before
  fun setUp() {
    AuthEmulator.auth.signOut()
    AuthEmulator.clearAccounts()
  }

  @After
  fun tearDown() {
    AuthEmulator.auth.signOut()
    AuthEmulator.clearAccounts()
  }

  @Test
  fun signOut_clearsTheSessionAndTheCredentialsThenReportsIt() {
    runBlocking { repository.signInWithGoogle(googleCredential()).getOrThrow() }
    assertNotNull(AuthEmulator.auth.currentUser)
    var signedOut = 0
    composeTestRule.setContent {
      ShifterTheme {
        SettingsRoute(
            onBack = {},
            onSignedOut = { signedOut++ },
            onModeChanged = {},
            viewModel =
                viewModel(factory = SettingsViewModel.factory(repositories, UserMode.VOLUNTEER)),
            credentialManager = credentialManager,
        )
      }
    }

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.SIGN_OUT_BUTTON).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) { signedOut == 1 }
    assertNull(AuthEmulator.auth.currentUser)
    assertEquals(1, credentialManager.clearCalls)
  }

  @Test
  fun changeMode_savesTheOtherModeThenReportsIt() {
    val modes = ModeRecorder()
    val changedTo = mutableListOf<UserMode>()
    setRoute(modes, onModeChanged = { changedTo += it })

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.CHANGE_MODE_BUTTON).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) { changedTo.isNotEmpty() }
    assertEquals(listOf(UserMode.ORGANIZER), modes.saved)
    assertEquals(listOf(UserMode.ORGANIZER), changedTo)
  }

  @Test
  fun changeMode_whenItFails_showsTheSnackbarAndRetryRunsItAgain() {
    val modes = ModeRecorder(failuresLeft = 1)
    val changedTo = mutableListOf<UserMode>()
    setRoute(modes, onModeChanged = { changedTo += it })
    val error = context.getString(R.string.settings_change_mode_error)

    composeTestRule.onNodeWithTag(SettingsScreenTestTags.CHANGE_MODE_BUTTON).performClick()
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty()
    }
    assertEquals(emptyList<UserMode>(), changedTo)

    composeTestRule.onNodeWithText(context.getString(R.string.settings_retry)).performClick()

    composeTestRule.waitUntil(TIMEOUT_MILLIS) { changedTo.isNotEmpty() }
    assertEquals(listOf(UserMode.ORGANIZER), changedTo)
  }

  private fun setRoute(modes: ModeRecorder, onModeChanged: (UserMode) -> Unit) {
    runBlocking { repository.signInWithGoogle(googleCredential()).getOrThrow() }
    val viewModel =
        SettingsViewModel(
            repository,
            modes,
            repositories.membershipRequestRepository,
            repositories.teamRepository,
            UserMode.VOLUNTEER,
            repositories.localSharingConsent,
        )
    composeTestRule.setContent {
      ShifterTheme {
        SettingsRoute(
            onBack = {},
            onSignedOut = {},
            onModeChanged = onModeChanged,
            viewModel = viewModel,
            credentialManager = credentialManager,
        )
      }
    }
  }

  /** Records the modes saved, failing the first [failuresLeft] times as when offline. */
  private class ModeRecorder(private var failuresLeft: Int = 0) : UserRepository {
    val saved = mutableListOf<UserMode>()

    override suspend fun setMode(uid: String, mode: UserMode) {
      if (failuresLeft > 0) {
        failuresLeft--
        throw UserRepositoryException.Unavailable()
      }
      saved += mode
    }

    override suspend fun getUser(uid: String): User? = null

    override suspend fun getOrCreateUser(
        uid: String,
        displayName: String,
        email: String,
        mode: UserMode?,
    ): User = throw UnsupportedOperationException()

    override suspend fun getPublicProfile(uid: String): PublicProfile? = null

    override suspend fun setLocationSharing(
        uid: String,
        enabled: Boolean,
        sharedIn: Collection<Team>,
    ) = throw UnsupportedOperationException()

    override fun observeLocationSharing(uid: String): Flow<Boolean> = flowOf(false)
  }

  /** The credential Credential Manager would return for a fresh fake Google account. */
  private fun googleCredential(): GoogleIdTokenCredential {
    val email = "volunteer-${UUID.randomUUID()}@example.com"
    return GoogleIdTokenCredential.Builder()
        .setId(email)
        .setIdToken(AuthEmulator.fakeGoogleIdToken("google-" + UUID.randomUUID(), email, "Ada"))
        .build()
  }

  /** A [CredentialManager] that only records that its state was cleared. */
  private class RecordingCredentialManager :
      CredentialManager by CredentialManager.create(
          InstrumentationRegistry.getInstrumentation().targetContext
      ) {
    var clearCalls = 0

    override suspend fun clearCredentialState(request: ClearCredentialStateRequest) {
      clearCalls++
    }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 20_000L
  }
}
