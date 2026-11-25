// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.settings

import com.swent.shifter.model.notification.NotificationKind
import com.swent.shifter.model.user.UserMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationGroupTest {

  @Test
  fun entries_holdEveryKindExactlyOnce() {
    val kinds = NotificationGroup.entries.flatMap { it.kinds }

    assertEquals(NotificationKind.entries.sorted(), kinds.sorted())
  }

  @Test
  fun shownIn_volunteerMode_showsTheVolunteerAndManagerGroups() {
    assertEquals(
        listOf(NotificationGroup.VOLUNTEER, NotificationGroup.MANAGER),
        NotificationGroup.shownIn(UserMode.VOLUNTEER),
    )
  }

  @Test
  fun shownIn_organizerMode_showsTheOrganizerGroup() {
    assertEquals(
        listOf(NotificationGroup.ORGANIZER),
        NotificationGroup.shownIn(UserMode.ORGANIZER),
    )
  }
}
