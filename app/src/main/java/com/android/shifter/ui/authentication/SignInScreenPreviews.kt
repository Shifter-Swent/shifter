// Based on Bootcamp authentication material.
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.swent.shifter.ui.authentication

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.credentials.Credential
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.user.PublicProfile
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.ui.theme.ShifterTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// The preview of the sign-in screen and the repositories it is built from, kept apart from the
// screen itself: they are tooling the IDE renders and no test ever runs, so coverage is told to
// leave this file alone.

@Preview(showBackground = true)
@Composable
fun SignInScreenPreview() {
  ShifterTheme {
    SignInScreen(
        authViewModel =
            viewModel(
                factory =
                    SignInViewModel.factory(
                        PreviewAuthRepository,
                        PreviewUserRepository,
                    )
            )
    )
  }
}

/** Signed out, and never asked to sign in: the preview only draws the screen. */
private object PreviewAuthRepository : AuthRepository {
  override suspend fun signInWithGoogle(credential: Credential): Result<AuthUser> =
      Result.failure(UnsupportedOperationException("Preview"))

  override fun currentUser(): AuthUser? = null

  override val authState: Flow<AuthUser?> = flowOf(null)

  override fun signOut(): Result<Unit> = Result.success(Unit)
}

/** Never reached: the preview never signs in. */
private object PreviewUserRepository : UserRepository {
  override suspend fun getUser(uid: String): User? = null

  override suspend fun getOrCreateUser(
      uid: String,
      displayName: String,
      email: String,
      mode: UserMode?,
  ): User = throw UnsupportedOperationException("Preview")

  override suspend fun setMode(uid: String, mode: UserMode) =
      throw UnsupportedOperationException("Preview")

  override suspend fun setLocationSharing(
      uid: String,
      enabled: Boolean,
      sharedIn: Collection<Team>,
  ) = throw UnsupportedOperationException("Preview")

  override fun observeLocationSharing(uid: String): Flow<Boolean> = flowOf(false)

  override suspend fun setNotificationEnabled(
      uid: String,
      kind: NotificationKind,
      enabled: Boolean,
  ) = throw UnsupportedOperationException("Preview")

  override fun observeDisabledNotifications(uid: String): Flow<Set<NotificationKind>> =
      flowOf(emptySet())

  override suspend fun getPublicProfile(uid: String): PublicProfile? = null
}
