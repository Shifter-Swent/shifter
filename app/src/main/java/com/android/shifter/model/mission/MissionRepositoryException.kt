// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

/**
 * Errors thrown by [MissionRepository] when the backend fails. Implementations translate their
 * backend errors into these, so ViewModels can handle failures without depending on Firebase.
 */
sealed class MissionRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /** The mission to change does not exist, e.g. because it was deleted meanwhile. */
  class NotFound(cause: Throwable? = null) :
      MissionRepositoryException("The mission does not exist", cause)

  /** The signed-in user may not access these missions. */
  class PermissionDenied(cause: Throwable? = null) :
      MissionRepositoryException("Access to the missions was denied", cause)

  /** The backend could not be reached, typically because the device is offline. */
  class Unavailable(cause: Throwable? = null) :
      MissionRepositoryException("The mission service is unavailable", cause)

  /** Any other backend failure. */
  class Unknown(cause: Throwable? = null) :
      MissionRepositoryException("Unexpected error while accessing the missions", cause)
}
