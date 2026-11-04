// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the contract the fake shares with the backend, so the ViewModels built against it see the
 * same messages and the same errors as with the Firestore repository.
 */
class FakeTeamChatRepositoryTest {

  private val repository = FakeTeamChatRepository(currentUserId = MEMBER, clock = { NOW })

  @Test
  fun send_recordsTheSenderTheTrimmedTextAndTheTime() = runTest {
    repository.send(EVENT_ID, TEAM_ID, "  Hello team  ")

    val message = repository.messages(EVENT_ID, TEAM_ID).first().single()
    assertEquals(MEMBER, message.senderId)
    assertEquals("Hello team", message.text)
    assertEquals(NOW, message.sentAt)
  }

  @Test
  fun send_givesEachMessageItsOwnId() = runTest {
    repository.send(EVENT_ID, TEAM_ID, "First")
    repository.send(EVENT_ID, TEAM_ID, "Second")

    val ids = repository.messages(EVENT_ID, TEAM_ID).first().map { it.id }
    assertEquals(2, ids.toSet().size)
  }

  @Test
  fun send_withABlankText_throwsAndSendsNothing() = runTest {
    assertThrowsSuspending<IllegalArgumentException> { repository.send(EVENT_ID, TEAM_ID, "  ") }

    assertEquals(emptyList<ChatMessage>(), repository.messages(EVENT_ID, TEAM_ID).first())
  }

  @Test
  fun send_withATooLongText_throwsAndSendsNothing() = runTest {
    assertThrowsSuspending<IllegalArgumentException> {
      repository.send(EVENT_ID, TEAM_ID, "a".repeat(ChatRules.MAX_TEXT_LENGTH + 1))
    }

    assertEquals(emptyList<ChatMessage>(), repository.messages(EVENT_ID, TEAM_ID).first())
  }

  @Test
  fun send_withABlankTextWhileTheBackendFails_throwsForTheText() = runTest {
    repository.failure = TeamChatRepositoryException.Unavailable()

    // The text is checked before the backend is reached, so the user is told what to fix rather
    // than to check their connection.
    assertThrowsSuspending<IllegalArgumentException> { repository.send(EVENT_ID, TEAM_ID, "  ") }
  }

  @Test
  fun messages_areOldestFirst() = runTest {
    repository.add(EVENT_ID, TEAM_ID, message("later", NOW.plusSeconds(60)))
    repository.add(EVENT_ID, TEAM_ID, message("earlier", NOW))

    val texts = repository.messages(EVENT_ID, TEAM_ID).first().map { it.text }

    assertEquals(listOf("earlier", "later"), texts)
  }

  @Test
  fun messages_sentAtTheSameTime_stayInSendingOrder() = runTest {
    repository.send(EVENT_ID, TEAM_ID, "First")
    repository.send(EVENT_ID, TEAM_ID, "Second")

    val texts = repository.messages(EVENT_ID, TEAM_ID).first().map { it.text }

    assertEquals(listOf("First", "Second"), texts)
  }

  @Test
  fun messages_withMoreThanTheMaximum_keepsTheMostRecent() = runTest {
    val count = ChatRules.MAX_MESSAGES + 5
    repeat(count) { i ->
      repository.add(EVENT_ID, TEAM_ID, message("$i", NOW.plusSeconds(i.toLong())))
    }

    val texts = repository.messages(EVENT_ID, TEAM_ID).first().map { it.text }

    assertEquals((5 until count).map { "$it" }, texts)
  }

  @Test
  fun messages_emitsOnlyTheChannelsMessagesAsTheyArrive() =
      runTest(UnconfinedTestDispatcher()) {
        val emissions = mutableListOf<List<ChatMessage>>()
        backgroundScope.launch { repository.messages(EVENT_ID, TEAM_ID).toList(emissions) }

        repository.send(EVENT_ID, TEAM_ID, "Hello team")
        repository.send(EVENT_ID, OTHER_TEAM_ID, "Other team")
        repository.send(OTHER_EVENT_ID, TEAM_ID, "Other event")

        // Only the message to the channel is emitted: the other two leave its list unchanged.
        assertEquals(
            listOf(emptyList(), listOf("Hello team")),
            emissions.map { messages -> messages.map { it.text } },
        )
      }

  @Test
  fun failure_isThrownByEveryCall() = runTest {
    val failure = TeamChatRepositoryException.NotAllowed()
    repository.failure = failure

    assertSame(
        failure,
        assertThrowsSuspending<TeamChatRepositoryException.NotAllowed> {
          repository.send(EVENT_ID, TEAM_ID, "Hello team")
        },
    )
    assertSame(
        failure,
        assertThrowsSuspending<TeamChatRepositoryException.NotAllowed> {
          repository.messages(EVENT_ID, TEAM_ID).first()
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
    const val MEMBER = "member-1"
    val NOW: Instant = Instant.parse("2026-11-04T18:02:00Z")
  }
}
