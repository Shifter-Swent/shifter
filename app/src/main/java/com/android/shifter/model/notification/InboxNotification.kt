// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * One document of a user's inbox, `users/{uid}/notifications/{id}`: something that happened for
 * them, written by the app of the person who acted, in the same transaction or batch as the action.
 *
 * It holds data, not text: the recipient's phone writes the text in its own language, with the
 * [NotificationFormat] of the [kind]. Free of Firebase types, so it can be tested without a
 * backend. It refuses, when built, what the rules refuse to store, with [requireStorable].
 *
 * @property id the document id.
 * @property kind always one with [NotificationKind.inInbox].
 * @property senderId the user whose action wrote it, checked by the rules to be the writer.
 * @property data the details of the [kind], under the keys of [NotificationData.Key], always
 *   strings.
 * @property createdAt when the server stored it.
 */
data class InboxNotification(
    val id: String,
    val kind: NotificationKind,
    val eventId: String,
    val eventTitle: String,
    val senderId: String,
    val data: Map<String, String>,
    val createdAt: Instant,
) {
  init {
    requireStorable(kind, data)
  }
}

/**
 * Throws [IllegalArgumentException] for what the rules refuse to store in an inbox, so a writer's
 * test fails here rather than the whole batch of its action on the server: a [kind] computed on the
 * phone, or a key of [data] outside [NotificationData.Key.ALL]. Checked once per action by
 * [InboxDocs.documents], even when it notifies nobody, so a writer's test catches it whoever the
 * recipients are.
 */
internal fun requireStorable(kind: NotificationKind, data: Map<String, String>) {
  require(kind.inInbox) { "$kind is computed on the phone, never written in an inbox" }
  require(NotificationData.Key.ALL.containsAll(data.keys)) { "Unknown data keys in $data" }
}

/**
 * [requireStorable], and also refuses a notification to its own sender, which the rules refuse too:
 * the one acting is never told about their own action. Checked by every writer, the fake inbox
 * included, before anything is written. [InboxDocs.recipients] already leaves the sender out, so
 * this check is only a safeguard.
 */
internal fun requireWritable(
    recipientId: String,
    senderId: String,
    kind: NotificationKind,
    data: Map<String, String>,
) {
  require(senderId != recipientId) { "Nobody writes in their own inbox" }
  requireStorable(kind, data)
}

/**
 * What goes in [InboxNotification.data]: the names of its entries in [Key], and the values a closed
 * entry takes in [Value]. Which kind uses which key is on each [NotificationKind].
 */
object NotificationData {

  /** The keys of [InboxNotification.data]. */
  object Key {
    const val USER_NAME = "userName"
    /** [Value.ACCEPTED] or [Value.REJECTED], for [NotificationKind.APPLICATION_DECIDED]. */
    const val DECISION = "decision"
    /**
     * [Value.JOINED] or [Value.LEFT] for [NotificationKind.TEAM_CHANGED], [Value.ADDED] or
     * [Value.REMOVED] for [NotificationKind.MISSION_ASSIGNED].
     */
    const val CHANGE = "change"
    const val TEAM_ID = "teamId"
    const val TEAM_NAME = "teamName"
    /** The missions concerned, one [MissionLine] per line. */
    const val MISSIONS = "missions"
    /**
     * [Value.TIME], [Value.PLACE] or [Value.TIME_AND_PLACE], for
     * [NotificationKind.MISSION_CHANGED]: what one action changed, in one notification.
     */
    const val WHAT = "what"
    const val ADDRESS = "address"
    /** A [com.swent.shifter.model.chat.ChannelKind] name, for [NotificationKind.ANNOUNCEMENT]. */
    const val CHANNEL = "channel"
    const val SENDER_NAME = "senderName"
    /** The start of the announcement's text. */
    const val PREVIEW = "preview"

    /** Every key: a document with another one is refused, by the fake inbox as by the rules. */
    val ALL =
        setOf(
            USER_NAME,
            DECISION,
            CHANGE,
            TEAM_ID,
            TEAM_NAME,
            MISSIONS,
            WHAT,
            ADDRESS,
            CHANNEL,
            SENDER_NAME,
            PREVIEW,
        )
  }

  /** The values of the closed keys: what a writer stores and a format compares. */
  object Value {
    const val ACCEPTED = "ACCEPTED"
    const val REJECTED = "REJECTED"
    const val JOINED = "JOINED"
    const val LEFT = "LEFT"
    const val ADDED = "ADDED"
    const val REMOVED = "REMOVED"
    const val TIME = "TIME"
    const val PLACE = "PLACE"
    const val TIME_AND_PLACE = "TIME_AND_PLACE"
  }
}

/**
 * One mission in [NotificationData.Key.MISSIONS], stored as `id|title|start|end`, the times in
 * ISO-8601. A notification keeps the title and the times as they are once the action is done (the
 * new ones for a mission moved, even if only its end moved), so the recipient's phone can show them
 * without reading the mission, which may have changed again or be gone.
 *
 * @property endAt never before [startAt]: a line built otherwise is refused, and one read otherwise
 *   is skipped.
 */
data class MissionLine(
    val id: String,
    val title: String,
    val startAt: Instant,
    val endAt: Instant,
) {
  init {
    require(!endAt.isBefore(startAt)) { "A mission ends after it starts" }
  }

  /**
   * The stored form of this line. The lines of a title are joined with spaces, its blank ones left
   * out, since a line break would cut the mission in two lines that [decodeAll] skips; a
   * notification shows it on one line anyway.
   */
  fun encode(): String {
    val oneLineTitle = title.lines().filter { it.isNotBlank() }.joinToString(" ")
    return listOf(id, oneLineTitle, startAt, endAt).joinToString(SEPARATOR.toString())
  }

  companion object {
    private const val SEPARATOR = '|'

    /** The stored form of [lines], one per line. */
    fun encodeAll(lines: List<MissionLine>): String = lines.joinToString("\n") { it.encode() }

    /**
     * Reads one stored line, or null when it is malformed. The id and the times never hold the
     * separator, so the line is cut at its first one and at its last two, and a title holding it
     * stays whole.
     */
    fun decode(line: String): MissionLine? {
      val first = line.indexOf(SEPARATOR)
      val last = line.lastIndexOf(SEPARATOR)
      val beforeLast = line.lastIndexOf(SEPARATOR, last - 1)
      if (first <= 0 || beforeLast <= first) return null
      val startAt = parseInstant(line.substring(beforeLast + 1, last)) ?: return null
      val endAt = parseInstant(line.substring(last + 1)) ?: return null
      if (endAt.isBefore(startAt)) return null
      return MissionLine(
          line.substring(0, first),
          line.substring(first + 1, beforeLast),
          startAt,
          endAt,
      )
    }

    private fun parseInstant(text: String): Instant? =
        try {
          Instant.parse(text)
        } catch (_: DateTimeParseException) {
          null
        }

    /** Reads the lines stored by [encodeAll], skipping the malformed ones. */
    fun decodeAll(stored: String): List<MissionLine> =
        stored.lines().filter { it.isNotEmpty() }.mapNotNull(::decode)
  }
}
