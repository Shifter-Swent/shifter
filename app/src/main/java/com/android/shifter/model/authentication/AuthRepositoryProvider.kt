// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>

package com.swent.shifter.model.authentication

import com.swent.shifter.model.RepositoryProviderFactory

/**
 * Supplies the [AuthRepository] used by the app.
 *
 * Temporary: the sign-in and settings screens are still created with `viewModel()`, with no
 * composition root to inject from. It reads the central container rather than building a second
 * Firebase repository, so production construction stays centralized in [RepositoryProviderFactory].
 * Removing it is tracked by #177.
 */
object AuthRepositoryProvider {
  val repository: AuthRepository
    get() = RepositoryProviderFactory.production.authRepository
}
