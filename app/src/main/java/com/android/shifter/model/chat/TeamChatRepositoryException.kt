// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

/**
 * Errors thrown by [TeamChatRepository]. Implementations translate their backend errors into these,
 * so ViewModels can handle failures without depending on Firebase.
 */
sealed class TeamChatRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /**
   * The signed-in user may not read or send to this channel: they are neither a member nor the
   * manager of the team, nor the event's organizer. Also thrown when the team does not exist, which
   * the backend does not tell apart.
   */
  class NotAllowed(cause: Throwable? = null) :
      TeamChatRepositoryException("Access to the team chat is not allowed", cause)

  /** The backend could not be reached, typically because the device is offline. */
  class Unavailable(cause: Throwable? = null) :
      TeamChatRepositoryException("The team chat is unavailable", cause)

  /** Any other failure, including a stored message that cannot be read. */
  class Unknown(cause: Throwable? = null) :
      TeamChatRepositoryException("Unexpected error while accessing the team chat", cause)
}
