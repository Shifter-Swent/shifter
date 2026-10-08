// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

/**
 * Errors thrown by [MembershipRequestRepository]. Implementations translate their backend errors
 * into these, so ViewModels can handle failures without depending on Firebase.
 */
sealed class MembershipRequestRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /**
   * The signed-in user may not access this request, or the event does not exist: the backend does
   * not tell the two apart, so look the event up first.
   */
  class PermissionDenied(cause: Throwable? = null) :
      MembershipRequestRepositoryException("Access to the membership request was denied", cause)

  /** The backend could not be reached, typically because the device is offline. */
  class Unavailable(cause: Throwable? = null) :
      MembershipRequestRepositoryException("The membership service is unavailable", cause)

  /** Any other failure, including a stored request that cannot be read. */
  class Unknown(cause: Throwable? = null) :
      MembershipRequestRepositoryException(
          "Unexpected error while accessing the membership request",
          cause,
      )
}
