// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.checkin

/**
 * Errors thrown by [CheckInRepository]. Implementations translate their backend errors into these,
 * so ViewModels can handle failures without depending on Firebase.
 */
sealed class CheckInRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /** The volunteer is already checked in to this mission; the existing check-in is kept. */
  class AlreadyCheckedIn(cause: Throwable? = null) :
      CheckInRepositoryException("The volunteer is already checked in to this mission", cause)

  /**
   * The signed-in user may not record or read this check-in: the volunteer is not assigned to the
   * mission, a volunteer tried to check someone else in, or the user is neither the manager of the
   * mission's team nor the organizer. Also thrown when the mission does not exist, which the
   * backend does not tell apart.
   */
  class NotAllowed(cause: Throwable? = null) :
      CheckInRepositoryException("The check-in is not allowed", cause)

  /** The backend could not be reached, typically because the device is offline. */
  class Unavailable(cause: Throwable? = null) :
      CheckInRepositoryException("The check-in service is unavailable", cause)

  /** Any other failure, including a stored check-in that cannot be read. */
  class Unknown(cause: Throwable? = null) :
      CheckInRepositoryException("Unexpected error while accessing the check-ins", cause)
}
