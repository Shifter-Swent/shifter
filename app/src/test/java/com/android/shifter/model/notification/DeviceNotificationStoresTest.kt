// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceNotificationStoresTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  private val first =
      inboxNotification(
          id = "n1",
          data = mapOf(NotificationData.Key.USER_NAME to "Ada"),
          createdAt = Instant.parse("2026-11-26T08:00:00.123Z"),
      )
  private val second =
      inboxNotification(id = "n2", createdAt = Instant.parse("2026-11-26T09:00:00Z"))

  @Test
  fun shownItems_areReadBackByAnotherInstance_asByAClosedApp() {
    SharedPreferencesShownItems(context, "u1").put("NEW_APPLICATION:e1", listOf(first, second))

    assertEquals(
        listOf(first, second),
        SharedPreferencesShownItems(context, "u1").items("NEW_APPLICATION:e1"),
    )
  }

  @Test
  fun shownItems_ofAnIdentityNeverPut_areNone() {
    assertEquals(
        emptyList<InboxNotification>(),
        SharedPreferencesShownItems(context, "u1").items("x"),
    )
  }

  @Test
  fun shownItems_put_replacesTheItemsOfThatIdentity() {
    val items = SharedPreferencesShownItems(context, "u1")
    items.put("NEW_APPLICATION:e1", listOf(first))
    items.put("NEW_APPLICATION:e1", listOf(second))

    assertEquals(listOf(second), items.items("NEW_APPLICATION:e1"))
  }

  @Test
  fun shownItems_retainOnly_forgetsTheOtherIdentities() {
    val items = SharedPreferencesShownItems(context, "u1")
    items.put("NEW_APPLICATION:e1", listOf(first))
    items.put("NEW_APPLICATION:e2", listOf(second))

    items.retainOnly(setOf("NEW_APPLICATION:e2"))

    assertEquals(emptyList<InboxNotification>(), items.items("NEW_APPLICATION:e1"))
    assertEquals(listOf(second), items.items("NEW_APPLICATION:e2"))
  }

  @Test
  fun shownItems_ofTwoUsers_neverMix() {
    SharedPreferencesShownItems(context, "u1").put("NEW_APPLICATION:e1", listOf(first))

    assertEquals(
        emptyList<InboxNotification>(),
        SharedPreferencesShownItems(context, "u2").items("NEW_APPLICATION:e1"),
    )
  }

  @Test
  fun shownItems_thatCannotBeRead_areNone() {
    context.getSharedPreferences("notification_shown_items_u1", Context.MODE_PRIVATE).edit {
      putString("unreadable", "not json")
      putString(
          "unknownKind",
          """[{"id":"n1","kind":"GONE","eventId":"e","eventTitle":"E",""" +
              """"senderId":"s","data":{},"createdAt":0}]""",
      )
    }
    val items = SharedPreferencesShownItems(context, "u1")

    assertEquals(emptyList<InboxNotification>(), items.items("unreadable"))
    assertEquals(emptyList<InboxNotification>(), items.items("unknownKind"))
  }

  @Test
  fun seenStore_marks_areReadBackByAnotherInstance() {
    SharedPreferencesSeenStore(context, "u1", NotificationKind.OPEN_SPOTS).markShown("m1")

    val seen = SharedPreferencesSeenStore(context, "u1", NotificationKind.OPEN_SPOTS)
    assertTrue(seen.wasShown("m1"))
    assertFalse(seen.wasShown("m2"))
  }

  @Test
  fun seenStore_forget_makesTheKeyNew() {
    val seen = SharedPreferencesSeenStore(context, "u1", NotificationKind.OPEN_SPOTS)
    seen.markShown("m1")

    seen.forget("m1")

    assertFalse(
        SharedPreferencesSeenStore(context, "u1", NotificationKind.OPEN_SPOTS).wasShown("m1")
    )
  }

  @Test
  fun seenStore_ofTwoKindsOrTwoUsers_neverMix() {
    SharedPreferencesSeenStore(context, "u1", NotificationKind.OPEN_SPOTS).markShown("m1")

    assertFalse(
        SharedPreferencesSeenStore(context, "u1", NotificationKind.MISSED_CHECK_IN).wasShown("m1")
    )
    assertFalse(
        SharedPreferencesSeenStore(context, "u2", NotificationKind.OPEN_SPOTS).wasShown("m1")
    )
  }
}
