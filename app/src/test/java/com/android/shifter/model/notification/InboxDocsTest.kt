// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * [InboxDocs.fields] refuses what the rules refuse, so a writer's test fails on it, and
 * [InboxDocs.recipients] leaves out what the rules would refuse a writer for. What the rules accept
 * is checked on the emulator by `InboxNotificationFirestoreMapperTest`.
 */
class InboxDocsTest {

  @Test
  fun fields_holdTheNotificationAndAServerTimestamp() {
    val data = mapOf(NotificationData.Key.USER_NAME to "Alice")

    val fields = fields(data = data)

    assertEquals(
        mapOf(
            "kind" to "NEW_APPLICATION",
            "eventId" to "event-1",
            "eventTitle" to "Festival",
            "senderId" to SENDER,
            "data" to data,
            "createdAt" to FieldValue.serverTimestamp(),
        ),
        fields,
    )
  }

  @Test
  fun fields_toItsOwnSender_areRefused() {
    assertThrows(IllegalArgumentException::class.java) { fields(recipientId = SENDER) }
  }

  @Test
  fun fields_ofAKindComputedOnThePhone_areRefused() {
    for (kind in NotificationKind.entries.filterNot { it.inInbox }) {
      assertThrows(IllegalArgumentException::class.java) { fields(kind = kind) }
    }
  }

  @Test
  fun fields_withAnUnknownDataKey_areRefused() {
    assertThrows(IllegalArgumentException::class.java) { fields(data = mapOf("title" to "Hi")) }
  }

  @Test
  fun recipients_leaveOutTheSenderAndTheDuplicates_inTheirOrder() {
    assertEquals(
        listOf("bob", "alice"),
        InboxDocs.recipients(SENDER, listOf("bob", SENDER, "alice", "bob")).toList(),
    )
  }

  @Test
  fun recipients_ofOnlyTheSender_areNone() {
    assertEquals(emptySet<String>(), InboxDocs.recipients(SENDER, listOf(SENDER)))
  }

  private fun fields(
      recipientId: String = "recipient-1",
      kind: NotificationKind = NotificationKind.NEW_APPLICATION,
      data: Map<String, String> = emptyMap(),
  ) = InboxDocs.fields(recipientId, kind, "event-1", "Festival", SENDER, data)

  private companion object {
    const val SENDER = "sender-1"
  }
}
