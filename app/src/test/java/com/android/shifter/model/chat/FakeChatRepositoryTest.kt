// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the contract the fake shares with the backend, so the ViewModels built against it see the
 * same messages and the same errors as with the Firestore repository.
 */
class FakeChatRepositoryTest {

  private val repository = FakeChatRepository(currentUserId = MEMBER, clock = { NOW })

  @Test
  fun send_recordsTheSenderTheTrimmedTextAndTheTime() = runTest {
    repository.send(CHANNEL, "  Hello team  ")

    val message = repository.messages(CHANNEL).first().single()
    assertEquals(MEMBER, message.senderId)
    assertEquals("Hello team", message.text)
    assertEquals(NOW, message.sentAt)
    assertFalse(message.pending)
  }

  @Test
  fun send_givesEachMessageItsOwnId() = runTest {
    repository.send(CHANNEL, "First")
    repository.send(CHANNEL, "Second")

    val ids = repository.messages(CHANNEL).first().map { it.id }
    assertEquals(2, ids.toSet().size)
  }

  @Test
  fun send_withABlankText_throwsAndSendsNothing() = runTest {
    assertThrowsSuspending<IllegalArgumentException> { repository.send(CHANNEL, "  ") }

    assertEquals(emptyList<ChatMessage>(), repository.messages(CHANNEL).first())
  }

  @Test
  fun send_withATooLongText_throwsAndSendsNothing() = runTest {
    assertThrowsSuspending<IllegalArgumentException> {
      repository.send(CHANNEL, "a".repeat(ChatRules.MAX_TEXT_LENGTH + 1))
    }

    assertEquals(emptyList<ChatMessage>(), repository.messages(CHANNEL).first())
  }

  @Test
  fun send_withABlankTextWhileTheBackendFails_throwsForTheText() = runTest {
    repository.failure = ChatRepositoryException.Unavailable()

    // The text is checked before the backend is reached, so the user is told what to fix rather
    // than to check their connection.
    assertThrowsSuspending<IllegalArgumentException> { repository.send(CHANNEL, "  ") }
  }

  @Test
  fun messages_areOldestFirst() = runTest {
    repository.add(CHANNEL, message("later", NOW.plusSeconds(60)))
    repository.add(CHANNEL, message("earlier", NOW))

    val texts = repository.messages(CHANNEL).first().map { it.text }

    assertEquals(listOf("earlier", "later"), texts)
  }

  @Test
  fun messages_sentAtTheSameTime_stayInSendingOrder() = runTest {
    repository.send(CHANNEL, "First")
    repository.send(CHANNEL, "Second")

    val texts = repository.messages(CHANNEL).first().map { it.text }

    assertEquals(listOf("First", "Second"), texts)
  }

  @Test
  fun messages_withMoreThanTheMaximum_keepsTheMostRecent() = runTest {
    val count = ChatRules.MAX_MESSAGES + 5
    repeat(count) { i -> repository.add(CHANNEL, message("$i", NOW.plusSeconds(i.toLong()))) }

    val texts = repository.messages(CHANNEL).first().map { it.text }

    assertEquals((5 until count).map { "$it" }, texts)
  }

  @Test
  fun messages_emitsOnlyTheChannelsMessagesAsTheyArrive() =
      runTest(UnconfinedTestDispatcher()) {
        val emissions = mutableListOf<List<ChatMessage>>()
        backgroundScope.launch { repository.messages(CHANNEL).toList(emissions) }

        repository.send(CHANNEL, "Hello team")
        repository.send(ChatChannelId.Team(EVENT_ID, OTHER_TEAM_ID), "Other team")
        repository.send(ChatChannelId.Team(OTHER_EVENT_ID, TEAM_ID), "Other event")
        repository.send(ChatChannelId.General(EVENT_ID), "Everyone")

        // Only the message to the channel is emitted: the other ones leave its list unchanged.
        assertEquals(
            listOf(emptyList(), listOf("Hello team")),
            emissions.map { messages -> messages.map { it.text } },
        )
      }

  @Test
  fun messages_ofTheGeneralChannel_listsOnlyTheMessagesOfItsEvent() = runTest {
    repository.send(ChatChannelId.General(EVENT_ID), "Hello everyone")
    repository.send(CHANNEL, "Hello team")
    repository.send(ChatChannelId.General(OTHER_EVENT_ID), "Other event")

    val texts = repository.messages(ChatChannelId.General(EVENT_ID)).first().map { it.text }

    assertEquals(listOf("Hello everyone"), texts)
  }

  @Test
  fun send_withAConfirmationPending_showsTheMessageAsPendingUntilConfirmed() =
      runTest(UnconfinedTestDispatcher()) {
        val confirmation = CompletableDeferred<Unit>()
        repository.confirmation = confirmation

        val sending = launch { repository.send(CHANNEL, "Hello team") }

        assertFalse(sending.isCompleted)
        val pending = repository.messages(CHANNEL).first().single()
        assertEquals("Hello team", pending.text)
        assertTrue(pending.pending)
        confirmation.complete(Unit)
        assertTrue(sending.isCompleted)
        assertFalse(repository.messages(CHANNEL).first().single().pending)
      }

  @Test
  fun send_refusedOnceConfirmed_takesTheMessageOutAndThrows() = runTest {
    repository.confirmation =
        CompletableDeferred<Unit>().apply {
          completeExceptionally(ChatRepositoryException.NotAllowed())
        }

    assertThrowsSuspending<ChatRepositoryException.NotAllowed> {
      repository.send(CHANNEL, "Hello team")
    }
    assertEquals(emptyList<ChatMessage>(), repository.messages(CHANNEL).first())
  }

  @Test
  fun send_notConfirmedInTime_keepsTheMessagePendingUntilTheBackendHasIt() = runTest {
    repository.confirmation =
        CompletableDeferred<Unit>().apply {
          completeExceptionally(ChatRepositoryException.Unavailable())
        }

    assertThrowsSuspending<ChatRepositoryException.Unavailable> {
      repository.send(CHANNEL, "Hello team")
    }
    // Not confirmed is not "not sent": the queued message is still shown, and goes out later.
    val queued = repository.messages(CHANNEL).first().single()
    assertEquals("Hello team", queued.text)
    assertTrue(queued.pending)

    repository.confirmPending()
    assertFalse(repository.messages(CHANNEL).first().single().pending)
  }

  @Test
  fun failure_whileCollected_endsTheFlowAfterItsMessages() =
      runTest(UnconfinedTestDispatcher()) {
        val emissions = mutableListOf<List<ChatMessage>>()
        val removed = ChatRepositoryException.NotAllowed()
        val collection = async { runCatching { repository.messages(CHANNEL).toList(emissions) } }
        repository.send(CHANNEL, "Hello team")

        // The member is removed from the team while the channel is open.
        repository.failure = removed

        assertSame(removed, collection.await().exceptionOrNull())
        assertEquals(
            listOf(emptyList(), listOf("Hello team")),
            emissions.map { messages -> messages.map { it.text } },
        )
      }

  @Test
  fun failure_isThrownByEveryCall() = runTest {
    val failure = ChatRepositoryException.NotAllowed()
    repository.failure = failure

    assertSame(
        failure,
        assertThrowsSuspending<ChatRepositoryException.NotAllowed> {
          repository.send(CHANNEL, "Hello team")
        },
    )
    assertSame(
        failure,
        assertThrowsSuspending<ChatRepositoryException.NotAllowed> {
          repository.messages(CHANNEL).first()
        },
    )
  }

  /** Runs [block] and returns what it threw, failing the test unless it is a [T]. */
  private suspend inline fun <reified T : Throwable> assertThrowsSuspending(
      block: suspend () -> Unit
  ): T {
    val thrown = runCatching { block() }.exceptionOrNull()
    assertTrue("Expected ${T::class.simpleName} but got $thrown", thrown is T)
    return thrown as T
  }

  private fun message(text: String, sentAt: Instant) = ChatMessage(text, MEMBER, text, sentAt)

  private companion object {
    const val EVENT_ID = "event-1"
    const val OTHER_EVENT_ID = "event-2"
    const val TEAM_ID = "team-1"
    const val OTHER_TEAM_ID = "team-2"
    val CHANNEL = ChatChannelId.Team(EVENT_ID, TEAM_ID)
    const val MEMBER = "member-1"
    val NOW: Instant = Instant.parse("2026-11-04T18:02:00Z")
  }
}
