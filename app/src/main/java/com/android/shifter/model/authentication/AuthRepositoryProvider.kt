// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.swent.shifter.model.authentication

/**
 * Supplies the [AuthRepository] used by the app.
 *
 * ViewModels only see the interface; choosing the Firebase implementation stays in `model/`.
 */
object AuthRepositoryProvider {
  val repository: AuthRepository by lazy { AuthRepositoryFirebase() }
}
