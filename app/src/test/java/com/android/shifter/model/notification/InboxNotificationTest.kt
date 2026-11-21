// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class InboxNotificationTest {

  private fun notification(kind: NotificationKind, data: Map<String, String>) =
      InboxNotification("n1", kind, "e1", "Fest", "organizer", data, Instant.EPOCH)

  @Test
  fun everyInboxKind_withItsKeys_isAccepted() {
    val decided = mapOf(NotificationData.Key.DECISION to NotificationData.Value.ACCEPTED)
    val inboxKinds = NotificationKind.entries.filter { it.inInbox }

    for (kind in inboxKinds) {
      assertEquals(kind, notification(kind, decided).kind)
    }
    assertEquals(6, inboxKinds.size)
  }

  @Test(expected = IllegalArgumentException::class)
  fun aMissedCheckIn_isRefused_sinceThePhoneComputesIt() {
    notification(NotificationKind.MISSED_CHECK_IN, emptyMap())
  }

  @Test(expected = IllegalArgumentException::class)
  fun openSpots_areRefused_sinceThePhoneComputesThem() {
    notification(NotificationKind.OPEN_SPOTS, emptyMap())
  }

  @Test(expected = IllegalArgumentException::class)
  fun anUnknownDataKey_isRefused() {
    notification(NotificationKind.NEW_APPLICATION, mapOf("userNmae" to "Alice"))
  }
}
