// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.model.user.UserMode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AppNotificationTest {

  private fun shown(kind: NotificationKind, key: String) =
      AppNotification(
          kind,
          key,
          "Title",
          "Text",
          Instant.EPOCH,
          NotificationTarget.Mission("e1", "Fest", UserMode.ORGANIZER, "m1"),
      )

  @Test
  fun theSameKindAndKey_shareTheirIdentity() {
    assertEquals(
        shown(NotificationKind.OPEN_SPOTS, "m1").identity,
        shown(NotificationKind.OPEN_SPOTS, "m1").copy(text = "Still short").identity,
    )
  }

  @Test
  fun twoKinds_withTheSameKey_neverShareTheirIdentity() {
    assertNotEquals(
        shown(NotificationKind.OPEN_SPOTS, "m1").identity,
        shown(NotificationKind.MISSION_CHANGED, "m1").identity,
    )
  }
}
