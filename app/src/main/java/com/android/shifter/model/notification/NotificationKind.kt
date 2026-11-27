// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

/**
 * What a notification is about. Each kind has its own Android channel and its own switch in
 * Settings, so a user can turn one off without losing the others. Each inbox kind lists the keys of
 * its [InboxNotification.data], named in [NotificationData.Key], and the task that writes it; a
 * format reads a key marked "only" as absent elsewhere.
 *
 * @property inInbox whether the kind travels through the recipient's inbox, written by the app of
 *   the person who acts. The others are computed on the recipient's phone by a [TimedCheck], from
 *   the current time, so nobody writes them.
 */
enum class NotificationKind(val inInbox: Boolean) {
  /** A volunteer applied to an event: for its organizer. Data: `userName`. Written by #595. */
  NEW_APPLICATION(inInbox = true),
  /**
   * The organizer accepted or rejected an application: for the volunteer. Data: `decision`. Written
   * by #603.
   */
  APPLICATION_DECIDED(inInbox = true),
  /**
   * Someone joined or left a team: for its manager. Data: `change` (JOINED or LEFT), `userName`,
   * `teamId`, `teamName`, and `missions` for LEFT only. Written by #599.
   */
  TEAM_CHANGED(inInbox = true),
  /**
   * A volunteer was added to or removed from missions: for that volunteer. Data: `change` (ADDED or
   * REMOVED), `missions`. Written by #617 and #606.
   */
  MISSION_ASSIGNED(inInbox = true),
  /**
   * The time or the place of a mission changed, or both in one action: for its assignees. Data:
   * `missions`, `what`, and `address` for PLACE and TIME_AND_PLACE only, absent when the mission no
   * longer has a place. Written by #617.
   */
  MISSION_CHANGED(inInbox = true),
  /**
   * An announcement was sent in a channel: for the channel's members. Data: `channel`, `teamId` and
   * `teamName` for a team's channel only, `senderName` when the sender has a name, `preview`.
   * Written by #618.
   */
  ANNOUNCEMENT(inInbox = true),
  /** A mission started and its volunteer has not checked in. Computed by #611. */
  MISSED_CHECK_IN(inInbox = false),
  /** A mission starts soon and still has open spots. Computed by #608. */
  OPEN_SPOTS(inInbox = false),
}
