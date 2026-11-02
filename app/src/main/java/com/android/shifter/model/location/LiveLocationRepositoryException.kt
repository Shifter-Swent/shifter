// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

/**
 * Errors thrown by [LiveLocationRepository] when the backend fails. Implementations translate their
 * backend errors into these, so ViewModels can handle failures without depending on Firebase.
 */
sealed class LiveLocationRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /**
   * The signed-in user may not share or read this position: their location sharing is off, they are
   * not in the team, the event is not running, or they are neither the manager of the team nor the
   * organizer. A publisher must stop and clean up rather than retry.
   *
   * The backend does not tell those cases apart, so a measure refused for being older than the one
   * already stored arrives here too: the publisher avoids it by never sending a measure older than
   * the last one it sent.
   */
  class PermissionDenied(cause: Throwable? = null) :
      LiveLocationRepositoryException("Access to the live locations was denied", cause)

  /**
   * The backend could not be reached, typically because the device is offline. Retrying is fine.
   */
  class Unavailable(cause: Throwable? = null) :
      LiveLocationRepositoryException("The live location service is unavailable", cause)

  /** Any other backend failure. */
  class Unknown(cause: Throwable? = null) :
      LiveLocationRepositoryException("Unexpected error while accessing the live locations", cause)
}
