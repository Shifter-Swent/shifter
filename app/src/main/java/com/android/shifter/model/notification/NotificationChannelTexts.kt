// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import androidx.annotation.StringRes
import com.swent.shifter.R

/**
 * The name of this kind's switch in Settings (#620), which also names its Android channel (#614),
 * so the user finds the same words in both places.
 */
@get:StringRes
val NotificationKind.channelName: Int
  get() =
      when (this) {
        NotificationKind.NEW_APPLICATION -> R.string.notification_channel_new_application
        NotificationKind.APPLICATION_DECIDED -> R.string.notification_channel_application_decided
        NotificationKind.TEAM_CHANGED -> R.string.notification_channel_team_changed
        NotificationKind.MISSION_ASSIGNED -> R.string.notification_channel_mission_assigned
        NotificationKind.MISSION_CHANGED -> R.string.notification_channel_mission_changed
        NotificationKind.ANNOUNCEMENT -> R.string.notification_channel_announcement
        NotificationKind.MISSED_CHECK_IN -> R.string.notification_channel_missed_check_in
        NotificationKind.OPEN_SPOTS -> R.string.notification_channel_open_spots
      }

/**
 * One line on when this kind is sent: under its switch in Settings, and as its channel's text. It
 * is formatted with [channelDescriptionArgs].
 */
@get:StringRes
val NotificationKind.channelDescription: Int
  get() =
      when (this) {
        NotificationKind.NEW_APPLICATION ->
            R.string.notification_channel_new_application_description
        NotificationKind.APPLICATION_DECIDED ->
            R.string.notification_channel_application_decided_description
        NotificationKind.TEAM_CHANGED -> R.string.notification_channel_team_changed_description
        NotificationKind.MISSION_ASSIGNED ->
            R.string.notification_channel_mission_assigned_description
        NotificationKind.MISSION_CHANGED ->
            R.string.notification_channel_mission_changed_description
        NotificationKind.ANNOUNCEMENT -> R.string.notification_channel_announcement_description
        NotificationKind.MISSED_CHECK_IN ->
            R.string.notification_channel_missed_check_in_description
        NotificationKind.OPEN_SPOTS -> R.string.notification_channel_open_spots_description
      }

/**
 * The values [channelDescription] is formatted with, read from the code that decides them, so the
 * text never says something else: the window of [NotificationKind.OPEN_SPOTS], in hours.
 */
val NotificationKind.channelDescriptionArgs: Array<Any>
  get() =
      when (this) {
        NotificationKind.OPEN_SPOTS -> arrayOf(OPEN_SPOTS_WINDOW.toHours())
        else -> emptyArray()
      }
