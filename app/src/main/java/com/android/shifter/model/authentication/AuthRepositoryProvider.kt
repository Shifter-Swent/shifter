// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>

package com.swent.shifter.model.authentication

import com.swent.shifter.model.RepositoryProviderFactory

/**
 * Supplies the [AuthRepository] used by the app.
 *
 * Temporary bridge: it now reads the one production container instead of building a second Firebase
 * repository of its own, so there is a single place where implementations are chosen. It only
 * exists because the sign-in and settings screens are still created with `viewModel()` and have no
 * composition root to inject from. Once the app has a NavHost that builds its ViewModels from
 * [RepositoryProviderFactory.production], this object goes away and those ViewModels take their
 * [AuthRepository] like every other one. ViewModels keep depending on the interface either way,
 * never on the container.
 */
object AuthRepositoryProvider {
  val repository: AuthRepository
    get() = RepositoryProviderFactory.production.authRepository
}
