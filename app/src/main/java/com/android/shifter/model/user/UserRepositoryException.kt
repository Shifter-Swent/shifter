// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.android.shifter.model.user

/**
 * Errors thrown by [UserRepository]. Implementations translate their backend errors into these, so
 * ViewModels can handle failures without depending on Firebase.
 */
sealed class UserRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /** The signed-in user is not allowed to access this profile. */
  class PermissionDenied(cause: Throwable? = null) :
      UserRepositoryException("Access to the user profile was denied", cause)

  /** The backend could not be reached, typically because the device is offline. */
  class Unavailable(cause: Throwable? = null) :
      UserRepositoryException("The user profile service is unavailable", cause)

  /** Any other failure. */
  class Unknown(cause: Throwable? = null) :
      UserRepositoryException("Unexpected error while accessing the user profile", cause)
}
