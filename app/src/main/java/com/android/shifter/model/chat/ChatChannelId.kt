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
   * organizer read and send, as the team is now. Only its manager sends announcements, not its
   * members nor the organizer, so a team with no manager has none.
   */
  data class Team(override val eventId: String, val teamId: String) : ChatChannelId

  /**
   * The general channel of [eventId], where everyone in the event talks: its organizer and its
   * accepted participants, the managers of its teams included, as they are now. A pending applicant
   * cannot read it, and someone who withdraws or is rejected loses access at once. Only the
   * organizer sends announcements, not the managers nor the participants.
   */
  data class General(override val eventId: String) : ChatChannelId

  /**
   * The admin channel of [eventId], where its organizer and the managers of its teams coordinate:
   * the managers as they are now, while they take part in the event. A volunteer who manages no
   * team cannot read it, and a manager who is replaced, withdraws or is rejected loses access at
   * once, as a new manager gets it. Only the organizer sends announcements, not the managers.
   */
  data class Admin(override val eventId: String) : ChatChannelId
}

/**
 * The kind of a [ChatChannelId], without its ids: what a screen or a route needs to name a channel
 * of an event it is already in.
 */
enum class ChannelKind {
  TEAM,
  GENERAL,
  ADMIN,
}
