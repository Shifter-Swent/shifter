// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

/**
 * Errors thrown by [NotificationInbox]. Implementations translate their backend errors into these,
 * so the notification engine handles failures without depending on Firebase.
 */
sealed class NotificationInboxException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /** The signed-in user may not read or empty this inbox: it is someone else's. */
  class NotAllowed(cause: Throwable? = null) :
      NotificationInboxException("Access to the notification inbox is not allowed", cause)

  /** The backend could not be reached, typically because the device is offline. */
  class Unavailable(cause: Throwable? = null) :
      NotificationInboxException("The notification inbox is unavailable", cause)

  /** Any other failure, including a stored notification that cannot be read. */
  class Unknown(cause: Throwable? = null) :
      NotificationInboxException("Unexpected error while accessing the notification inbox", cause)
}
