// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

/**
 * Errors thrown by [TeamRepository] when the backend fails. Implementations translate their backend
 * errors into these, so ViewModels can handle failures without depending on Firebase.
 */
sealed class TeamRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /** The team to change does not exist, e.g. because it was deleted meanwhile. */
  class NotFound(cause: Throwable? = null) :
      TeamRepositoryException("The team does not exist", cause)

  /** The signed-in user may not access these teams. */
  class PermissionDenied(cause: Throwable? = null) :
      TeamRepositoryException("Access to the teams was denied", cause)

  /** The backend could not be reached, typically because the device is offline. */
  class Unavailable(cause: Throwable? = null) :
      TeamRepositoryException("The team service is unavailable", cause)

  /** Any other backend failure. */
  class Unknown(cause: Throwable? = null) :
      TeamRepositoryException("Unexpected error while accessing the teams", cause)
}
