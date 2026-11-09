// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

/**
 * Which chat channel a [ChatRepository] reads or sends to. Every channel belongs to an event; who
 * may use it depends on the kind of channel, as described on each one.
 */
sealed interface ChatChannelId {

  /** The event the channel belongs to. */
  val eventId: String

  /**
   * The channel of the team [teamId] of [eventId]: its members, its manager and the event's
   * organizer read and send, as the team is now.
   */
  data class Team(override val eventId: String, val teamId: String) : ChatChannelId
}
