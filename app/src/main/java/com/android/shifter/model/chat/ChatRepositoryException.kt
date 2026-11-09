// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

/**
 * Errors thrown by [ChatRepository]. Implementations translate their backend errors into these, so
 * ViewModels can handle failures without depending on Firebase.
 */
sealed class ChatRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /**
   * The signed-in user may not read or send to this channel: they are not one of its members, given
   * by its [ChatChannelId]. Also thrown when the channel's team does not exist, which the backend
   * does not tell apart.
   */
  class NotAllowed(cause: Throwable? = null) :
      ChatRepositoryException("Access to the chat channel is not allowed", cause)

  /** The backend could not be reached, typically because the device is offline. */
  class Unavailable(cause: Throwable? = null) :
      ChatRepositoryException("The chat is unavailable", cause)

  /** Any other failure, including a stored message that cannot be read. */
  class Unknown(cause: Throwable? = null) :
      ChatRepositoryException("Unexpected error while accessing the chat", cause)
}
