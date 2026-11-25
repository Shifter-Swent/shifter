// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import androidx.annotation.StringRes
import com.swent.shifter.R
import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.user.UserMode

/**
 * The kinds of notifications of one role, under one title in the Notifications section of Settings.
 * Every [NotificationKind] is in exactly one group.
 */
enum class NotificationGroup(@StringRes val title: Int, val kinds: List<NotificationKind>) {
  VOLUNTEER(
      R.string.settings_notifications_as_volunteer,
      listOf(
          NotificationKind.APPLICATION_DECIDED,
          NotificationKind.MISSION_ASSIGNED,
          NotificationKind.MISSION_CHANGED,
          NotificationKind.ANNOUNCEMENT,
      ),
  ),
  MANAGER(
      R.string.settings_notifications_as_manager,
      listOf(NotificationKind.TEAM_CHANGED, NotificationKind.MISSED_CHECK_IN),
  ),
  ORGANIZER(
      R.string.settings_notifications_as_organizer,
      listOf(NotificationKind.NEW_APPLICATION, NotificationKind.OPEN_SPOTS),
  );

  companion object {
    /**
     * The groups Settings shows in [mode]: a manager is a volunteer, so the volunteer mode shows
     * the volunteer and manager groups, and the organizer mode the organizer group.
     */
    fun shownIn(mode: UserMode): List<NotificationGroup> =
        when (mode) {
          UserMode.VOLUNTEER -> listOf(VOLUNTEER, MANAGER)
          UserMode.ORGANIZER -> listOf(ORGANIZER)
        }
  }
}
