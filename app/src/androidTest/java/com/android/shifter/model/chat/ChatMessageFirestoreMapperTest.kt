// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Mapping tests for [toChatMessage], run against the Firestore emulator.
 *
 * The rules only accept well-formed messages, so the malformed bodies these tests need are written
 * with [FirestoreEmulatorAdmin]. The signed-in user is a member of the team, which the rules let
 * read its channel. A pending message is covered by `FirestoreChatRepositoryTest`.
 */
@RunWith(AndroidJUnit4::class)
class ChatMessageFirestoreMapperTest {

  private val auth = FirestoreEmulator.auth
  private val firestore = FirestoreEmulator.firestore
  private val teamPath = "events/event-${UUID.randomUUID()}/teams/team-bar"
  private val path = "$teamPath/messages/$MESSAGE_ID"
  private lateinit var uid: String

  @Before
  fun signIn() = emulatorTest {
    uid = checkNotNull(auth.signInForFirestore { auth.signInAnonymously().await() }.user).uid
    FirestoreEmulatorAdmin.createDocument(teamPath, mapOf("memberIds" to listOf(uid)))
  }

  @After
  fun tearDown() = emulatorTest {
    try {
      step("cleanup") {
        FirestoreEmulatorAdmin.deleteDocument(path)
        FirestoreEmulatorAdmin.deleteDocument(teamPath)
      }
    } finally {
      auth.signOut()
    }
  }

  @Test
  fun toChatMessage_mapsTheFieldsAndTheId() = emulatorTest {
    val mapped = storeAndRead(valid()).toChatMessage()

    assertEquals(ChatMessage(MESSAGE_ID, SENDER_ID, "Hello team", SENT_AT), mapped)
  }

  @Test
  fun toChatMessage_rejectsASentAtThatIsNotATimestamp() = emulatorTest {
    val stored = storeAndRead(valid() + ("sentAt" to "yesterday"))

    assertFailsNaming(stored, "'sentAt'")
  }

  @Test
  fun toChatMessage_rejectsAMissingField() = emulatorTest {
    for (field in listOf("senderId", "text", "sentAt")) {
      FirestoreEmulatorAdmin.deleteDocument(path)
      val stored = storeAndRead(valid() - field)

      assertFailsNaming(stored, "'$field'")
    }
  }

  /**
   * Asserts that mapping [snapshot] fails with an [IllegalStateException] whose message contains
   * [reason], so a test cannot pass because of another malformed field than the one it breaks.
   */
  private fun assertFailsNaming(snapshot: DocumentSnapshot, reason: String) {
    val failure = runCatching { snapshot.toChatMessage() }.exceptionOrNull()

    assertTrue(
        "a malformed document must fail with IllegalStateException, but was: $failure",
        failure is IllegalStateException,
    )
    assertTrue(
        "the failure must say $reason, but said: ${failure?.message}",
        failure?.message?.contains(reason) == true,
    )
  }

  private fun valid(): Map<String, Any> =
      mapOf("senderId" to SENDER_ID, "text" to "Hello team", "sentAt" to SENT_AT)

  private suspend fun storeAndRead(fields: Map<String, Any>): DocumentSnapshot {
    FirestoreEmulatorAdmin.createDocument(path, fields)
    return firestore.document(path).get(Source.SERVER).await()
  }

  private companion object {
    const val MESSAGE_ID = "message-1"
    const val SENDER_ID = "member-1"
    val SENT_AT: Instant = Instant.parse("2026-07-04T18:30:00Z")
  }
}
