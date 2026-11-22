// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.shift

/**
 * Errors thrown by [ShiftRepository]. Implementations translate their backend errors into these, so
 * ViewModels can handle failures without depending on Firebase.
 */
sealed class ShiftRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /**
   * The signed-in user may not do this, e.g. close a slot volunteers chose, or open one without
   * being the organizer: the backend does not tell the two apart.
   */
  class PermissionDenied(cause: Throwable? = null) :
      ShiftRepositoryException("Access to the shifts was denied", cause)

  /** The backend could not be reached, typically because the device is offline. */
  class Unavailable(cause: Throwable? = null) :
      ShiftRepositoryException("The shift service is unavailable", cause)

  /** Any other failure, including a stored shift that cannot be read. */
  class Unknown(cause: Throwable? = null) :
      ShiftRepositoryException("Unexpected error while accessing the shifts", cause)
}
