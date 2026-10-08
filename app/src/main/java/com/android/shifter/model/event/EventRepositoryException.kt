// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.event

/**
 * Errors thrown by [EventRepository] when the backend fails. Implementations translate their
 * backend errors into these, so ViewModels can handle failures without depending on Firebase.
 */
sealed class EventRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /** The signed-in user may not access these events. */
  class PermissionDenied(cause: Throwable? = null) :
      EventRepositoryException("Access to the events was denied", cause)

  /** The backend could not be reached, typically because the device is offline. */
  class Unavailable(cause: Throwable? = null) :
      EventRepositoryException("The event service is unavailable", cause)

  /** Any other backend failure. */
  class Unknown(cause: Throwable? = null) :
      EventRepositoryException("Unexpected error while accessing the events", cause)
}
