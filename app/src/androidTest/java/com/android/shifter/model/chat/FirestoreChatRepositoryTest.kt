// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.firestore.Source
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.firebase.emulatorTest
import com.swent.shifter.firebase.signInForFirestore
import com.swent.shifter.firebase.step
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FirestoreChatRepository] against the emulators, with the real `firestore.rules`.
 * `TeamChatRulesTest` covers each rule on its own; these tests check what the repository makes of
 * them: the document it writes, the errors it throws, and what its flow emits.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreChatRepositoryTest {
  private val auth = FirestoreEmulator.auth
  private val db = FirestoreEmulator.firestore
  private val repository = FirestoreChatRepository(db, auth)
  private val paths = mutableSetOf<String>()
  private lateinit var organizer: Account
  private lateinit var manager: Account
  private lateinit var member: Account
  private lateinit var outsider: Account
  private lateinit var eventId: String

  private val channel
    get() = ChatChannelId.Team(eventId, TEAM_ID)

  private val teamPath
    get() = "events/$eventId/teams/$TEAM_ID"

  @Before
  fun setUp() = emulatorTest {
    organizer = account()
    manager = account()
    member = account()
    outsider = account()
    eventId = event()
  }

  @After
  fun cleanUp() = emulatorTest {
    try {
      step("cleanup") {
        // The messages sent through the repository are only known by their path once listed. In
        // parallel, since a test stores over a hundred of them.
        val all = channelPaths() + paths
        coroutineScope {
          all.toSet().map { async { FirestoreEmulatorAdmin.deleteDocument(it) } }.awaitAll()
        }
      }
    } finally {
      auth.signOut()
    }
  }

  @Test
  fun send_storesTheTrimmedTextInTheSignedInUsersName() = emulatorTest {
    signIn(member)
    val before = Instant.now()

    repository.send(channel, "  Hello team  ")

    val stored = db.collection("$teamPath/messages").get(Source.SERVER).await().documents.single()
    assertEquals(setOf("senderId", "text", "sentAt"), stored.data?.keys)
    val message = repository.messages(channel).first().single()
    assertEquals(ChatMessage(stored.id, member.uid, "Hello team", message.sentAt), message)
    assertNear(before, message.sentAt)
  }

  @Test
  fun messages_emitsEmptyThenEachMessageOldestFirst() = emulatorTest {
    signIn(member)
    coroutineScope {
      val emissions = repository.messages(channel).produceIn(this)
      try {
        assertEquals(emptyList<ChatMessage>(), emissions.receive())

        repository.send(channel, "First")
        assertEquals(listOf("First"), emissions.receiveUntil { it.size == 1 }.texts())

        // Through the admin API, so the member stays signed in and the listener keeps their access.
        messageAsAdmin("second", manager, "Second", Instant.now().plus(Duration.ofHours(1)))
        val both = emissions.receiveUntil { it.size == 2 }
        assertEquals(listOf("First", "Second"), both.texts())
        assertEquals(listOf(member.uid, manager.uid), both.map { it.senderId })
      } finally {
        emissions.cancel()
      }
    }
  }

  @Test
  fun messages_withMoreThanTheMaximum_keepsTheMostRecent() =
      emulatorTest(FULL_CHANNEL_TIMEOUT_MILLIS) {
        val start = Instant.parse("2026-07-04T18:00:00Z")
        for (i in 0..ChatRules.MAX_MESSAGES) {
          messageAsAdmin("message-$i", member, "Message $i", start.plusSeconds(i.toLong()))
        }
        signIn(member)

        val listed = repository.messages(channel).first()

        assertEquals((1..ChatRules.MAX_MESSAGES).map { "Message $it" }, listed.texts())
      }

  @Test
  fun messagesAndSend_byAnOutsider_failWithNotAllowed() = emulatorTest {
    signIn(outsider)

    assertFailsWith<ChatRepositoryException.NotAllowed> { repository.messages(channel).first() }
    assertFailsWith<ChatRepositoryException.NotAllowed> { repository.send(channel, "Hello team") }
  }

  /** The promise of [ChatRepository.messages]: losing access ends an open observation. */
  @Test
  fun messages_afterBeingRemovedFromTheTeam_failsWithNotAllowed() = emulatorTest {
    signIn(member)
    var removed = false

    // Collected here rather than through a channel, whose producer would also fail this test's
    // scope with the very error it expects.
    val failure = runCatching {
      step("denial") {
        repository.messages(channel).collect {
          // Only once the listener is active, so the refusal cannot be it never having been
          // allowed.
          if (removed) return@collect
          removed = true
          // Through the admin API so the member stays signed in: signing the organizer in would
          // take the permission away by itself and prove nothing about the rules.
          FirestoreEmulatorAdmin.deleteDocument(teamPath)
          FirestoreEmulatorAdmin.createDocument(
              teamPath,
              mapOf("managerId" to manager.uid, "memberIds" to emptyList<String>()),
          )
          // Firestore checks the rules again when the query's result changes, not when the team
          // document does, so a message has to be sent for the listener to notice.
          messageAsAdmin("after", manager, "Briefing at 18:00", Instant.now())
        }
      }
    }

    assertTrue("the listener never emitted", removed)
    assertTrue(
        "Expected NotAllowed, got ${failure.exceptionOrNull()}",
        failure.exceptionOrNull() is ChatRepositoryException.NotAllowed,
    )
  }

  @Test
  fun messages_withAMalformedMessage_failsWithUnknown() = emulatorTest {
    // No client may write it: the rules only accept a well-formed message.
    FirestoreEmulatorAdmin.createDocument(
        "$teamPath/messages/malformed",
        mapOf("senderId" to member.uid, "sentAt" to Instant.now()),
    )
    signIn(member)

    assertFailsWith<ChatRepositoryException.Unknown> { repository.messages(channel).first() }
  }

  /**
   * The promise of [ChatRepository.send] offline: the message is listed at once as pending, the
   * call gives up with `Unavailable` rather than waiting, and the message still goes out once the
   * connection is back, which the flow reports by emitting it again, confirmed.
   */
  @Test
  fun send_whileOffline_listsThePendingMessageThenConfirmsItOnceOnline() =
      emulatorTest(OFFLINE_TIMEOUT_MILLIS) {
        signIn(member)
        coroutineScope {
          val emissions = repository.messages(channel).produceIn(this)
          try {
            assertEquals(emptyList<ChatMessage>(), emissions.receive())
            db.disableNetwork().await()
            val before = Instant.now()
            val sending =
                async(start = CoroutineStart.UNDISPATCHED) {
                  runCatching { repository.send(channel, "Hello team") }
                }

            val pending = emissions.receiveUntil { it.isNotEmpty() }.single()
            assertEquals("Hello team", pending.text)
            assertTrue("an unsent message must be marked pending", pending.pending)
            assertNear(before, pending.sentAt)
            val failure = step("offline send") { sending.await() }.exceptionOrNull()
            assertTrue(
                "Expected Unavailable, got $failure",
                failure is ChatRepositoryException.Unavailable,
            )

            db.enableNetwork().await()
            // Only its metadata changes when the server confirms it, which must still be emitted.
            val confirmed = emissions.receiveUntil { list -> list.none { it.pending } }.single()
            assertEquals(pending.id, confirmed.id)
            assertFalse(confirmed.pending)
          } finally {
            db.enableNetwork().await()
            db.waitForPendingWrites().await()
            emissions.cancel()
          }
        }
      }

  /** Creates an event of [organizer] with the team [TEAM_ID], led by [manager], of [member]. */
  private suspend fun event(): String {
    signIn(organizer)
    val ref = db.collection("events").document()
    paths += ref.path
    ref.set(mapOf("organizerId" to organizer.uid)).await()
    val team = ref.collection("teams").document(TEAM_ID)
    paths += team.path
    team.set(mapOf("managerId" to manager.uid, "memberIds" to listOf(member.uid))).await()
    return ref.id
  }

  /** Stores a message of [sender] sent at [sentAt], whatever the rules say about it. */
  private suspend fun messageAsAdmin(id: String, sender: Account, text: String, sentAt: Instant) {
    val path = "$teamPath/messages/$id"
    paths += path
    FirestoreEmulatorAdmin.createDocument(
        path,
        mapOf("senderId" to sender.uid, "text" to text, "sentAt" to sentAt),
    )
  }

  /** The paths of every message of the channel, read as the organizer, who may read them all. */
  private suspend fun channelPaths(): List<String> {
    signIn(organizer)
    return db.collection("$teamPath/messages").get(Source.SERVER).await().documents.map {
      it.reference.path
    }
  }

  private suspend fun account(): Account {
    val email = "team-chat-repo-${UUID.randomUUID()}@example.test"
    val password = "test-password-123"
    val user =
        checkNotNull(
            auth
                .signInForFirestore { auth.createUserWithEmailAndPassword(email, password).await() }
                .user
        )
    return Account(user.uid, EmailAuthProvider.getCredential(email, password))
  }

  private suspend fun signIn(user: Account) {
    auth.signInForFirestore { auth.signInWithCredential(user.credential).await() }
  }

  private fun List<ChatMessage>.texts() = map { it.text }

  /** Skips the emissions before the first one [predicate] accepts. */
  private suspend fun ReceiveChannel<List<ChatMessage>>.receiveUntil(
      predicate: (List<ChatMessage>) -> Boolean
  ): List<ChatMessage> {
    while (true) {
      val emission = receive()
      if (predicate(emission)) return emission
    }
  }

  private fun assertNear(expected: Instant, actual: Instant) {
    assertTrue(
        "Expected about $expected, got $actual",
        Duration.between(expected, actual).abs() <= CLOCK_TOLERANCE,
    )
  }

  private inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit) {
    val failure = runCatching { block() }.exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName}, got $failure", failure is T)
  }

  private data class Account(val uid: String, val credential: AuthCredential)

  private companion object {
    const val TEAM_ID = "team-bar"

    /**
     * Room for [FirestoreChatRepository.SEND_TIMEOUT_MILLIS] to expire for real, plus the round
     * trips around it: the default budget of an emulator test does not leave enough.
     */
    const val OFFLINE_TIMEOUT_MILLIS = 60_000L

    /**
     * Room to write a full channel, [ChatRules.MAX_MESSAGES] + 1 messages one request each, which
     * alone can take the default budget of an emulator test on the CI's emulator (#385).
     */
    const val FULL_CHANNEL_TIMEOUT_MILLIS = 90_000L

    /** The device and the emulator host keep their own clocks, so a little drift is allowed. */
    val CLOCK_TOLERANCE: Duration = Duration.ofMinutes(2)
  }
}
