// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import com.swent.shifter.model.chat.ChatMessage
import com.swent.shifter.model.chat.ChatRules
import com.swent.shifter.model.chat.FakeTeamChatRepository
import com.swent.shifter.model.chat.TeamChatRepositoryException
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TeamChatViewModelTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val chat = FakeTeamChatRepository(currentUserId = ME, clock = { SENT_AT.plusSeconds(60) })
  private val users =
      FakeUserRepository(listOf(user(ME, "Liam"), user(OTHER, "Alice"), user(NAMELESS, " ")))

  private fun viewModel() = TeamChatViewModel(EVENT, TEAM, chat, users) { ME }

  private fun receive(id: String, from: String, text: String) =
      chat.add(EVENT, TEAM, ChatMessage(id, from, text, SENT_AT))

  @Test
  fun load_showsTheMessagesWithTheirSenders() = runTest {
    receive("1", OTHER, "Hello")
    receive("2", ME, "Hi")

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertNull(state.error)
    assertEquals(listOf("Hello", "Hi"), state.messages.map { it.text })
    assertEquals(listOf("Alice", "Liam"), state.messages.map { it.senderName })
    assertEquals(listOf(false, true), state.messages.map { it.isMine })
    assertEquals(SENT_AT, state.messages.first().sentAt)
  }

  @Test
  fun load_withAnEmptyChannel_showsNoMessages() = runTest {
    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertTrue(state.messages.isEmpty())
  }

  @Test
  fun messages_fromAnotherMember_showUpInRealTime() = runTest {
    val viewModel = viewModel()

    receive("1", OTHER, "Anyone at gate B?")

    assertEquals(listOf("Anyone at gate B?"), viewModel.uiState.value.messages.map { it.text })
  }

  @Test
  fun messages_fromASenderWithoutName_haveNoName() = runTest {
    receive("1", NAMELESS, "Hello")
    receive("2", "unknown-user", "Hey")

    val names = viewModel().uiState.value.messages.map { it.senderName }

    assertEquals(listOf(null, null), names)
  }

  @Test
  fun messages_whenTheProfilesFail_areShownWithoutNames() = runTest {
    users.failure = IllegalStateException("boom")
    receive("1", OTHER, "Hello")

    val state = viewModel().uiState.value

    assertEquals(listOf("Hello"), state.messages.map { it.text })
    assertNull(state.messages.single().senderName)
  }

  @Test
  fun load_whenUnavailable_showsOffline() = runTest {
    chat.failure = TeamChatRepositoryException.Unavailable()

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertEquals(ChatError.OFFLINE, state.error)
  }

  @Test
  fun load_whenItFails_showsTheUnexpectedError() = runTest {
    chat.failure = TeamChatRepositoryException.Unknown()

    assertEquals(ChatError.UNEXPECTED, viewModel().uiState.value.error)
  }

  @Test
  fun load_whenNotAllowed_showsNotAllowed() = runTest {
    chat.failure = TeamChatRepositoryException.NotAllowed()

    assertEquals(ChatError.NOT_ALLOWED, viewModel().uiState.value.error)
  }

  @Test
  fun messages_whenAccessIsLost_hideTheMessagesAndShowNotAllowed() = runTest {
    receive("1", OTHER, "Hello")
    val viewModel = viewModel()

    chat.failure = TeamChatRepositoryException.NotAllowed()

    val state = viewModel.uiState.value
    assertEquals(ChatError.NOT_ALLOWED, state.error)
    assertTrue(state.messages.isEmpty())
  }

  @Test
  fun load_afterAnError_showsTheMessages() = runTest {
    chat.failure = TeamChatRepositoryException.Unavailable()
    val viewModel = viewModel()
    chat.failure = null
    receive("1", OTHER, "Hello")

    viewModel.load()

    assertNull(viewModel.uiState.value.error)
    assertEquals(listOf("Hello"), viewModel.uiState.value.messages.map { it.text })
  }

  @Test
  fun canSend_withBlankOrTooLongText_isFalse() {
    assertFalse(TeamChatUiState(draft = "   ").canSend)
    assertFalse(TeamChatUiState(draft = "a".repeat(ChatRules.MAX_TEXT_LENGTH + 1)).canSend)
    assertTrue(TeamChatUiState(draft = " ${"a".repeat(ChatRules.MAX_TEXT_LENGTH)} ").canSend)
  }

  @Test
  fun send_withBlankText_sendsNothing() = runTest {
    val viewModel = viewModel()
    viewModel.onDraftChange("   ")

    viewModel.send()

    assertTrue(viewModel.uiState.value.messages.isEmpty())
  }

  @Test
  fun send_online_showsTheMessageAtOnceAsSent() = runTest {
    val viewModel = viewModel()
    viewModel.onDraftChange("  On my way  ")

    viewModel.send()

    val state = viewModel.uiState.value
    assertEquals("", state.draft)
    val message = state.messages.single()
    assertEquals("On my way", message.text)
    assertTrue(message.isMine)
    assertFalse(message.isPending)
  }

  @Test
  fun send_offline_showsTheMessageAsPendingUntilConfirmed() = runTest {
    val confirmation = CompletableDeferred<Unit>()
    chat.confirmation = confirmation
    val viewModel = viewModel()
    viewModel.onDraftChange("On my way")

    viewModel.send()

    assertTrue(viewModel.uiState.value.messages.single().isPending)

    confirmation.complete(Unit)

    assertFalse(viewModel.uiState.value.messages.single().isPending)
  }

  @Test
  fun send_notConfirmedInTime_keepsTheMessagePendingAndDoesNotGiveTheTextBack() = runTest {
    val confirmation = CompletableDeferred<Unit>()
    chat.confirmation = confirmation
    val viewModel = viewModel()
    viewModel.onDraftChange("On my way")
    viewModel.send()

    confirmation.completeExceptionally(TeamChatRepositoryException.Unavailable())

    // Still queued by the backend: giving the text back would let the user post it twice.
    val state = viewModel.uiState.value
    assertEquals("", state.draft)
    assertFalse(state.sendFailed)
    assertTrue(state.messages.single().isPending)
  }

  @Test
  fun send_refusedOnceConfirmed_removesTheMessageAndGivesTheTextBack() = runTest {
    val confirmation = CompletableDeferred<Unit>()
    chat.confirmation = confirmation
    val viewModel = viewModel()
    viewModel.onDraftChange("On my way")
    viewModel.send()

    confirmation.completeExceptionally(TeamChatRepositoryException.NotAllowed())

    val state = viewModel.uiState.value
    assertEquals("On my way", state.draft)
    assertTrue(state.sendFailed)
    assertTrue(state.messages.isEmpty())
  }

  @Test
  fun send_offline_withTheSameTextAsAnOlderMessage_marksOnlyTheNewOneAsPending() = runTest {
    receive("old", ME, "OK")
    chat.confirmation = CompletableDeferred()
    val viewModel = viewModel()
    viewModel.onDraftChange("OK")

    viewModel.send()

    assertEquals(listOf(false, true), viewModel.uiState.value.messages.map { it.isPending })
  }

  @Test
  fun send_whenItFails_givesTheTextBack() = runTest {
    val viewModel = viewModel()
    chat.failure = TeamChatRepositoryException.NotAllowed()
    viewModel.onDraftChange("On my way")

    viewModel.send()

    val state = viewModel.uiState.value
    assertEquals("On my way", state.draft)
    assertTrue(state.sendFailed)
    assertTrue(state.messages.isEmpty())
  }

  @Test
  fun onDraftChange_clearsTheSendFailure() = runTest {
    val viewModel = viewModel()
    chat.failure = TeamChatRepositoryException.NotAllowed()
    viewModel.onDraftChange("On my way")
    viewModel.send()

    viewModel.onDraftChange("On my way!")

    assertFalse(viewModel.uiState.value.sendFailed)
  }

  private fun user(uid: String, name: String) =
      User(uid = uid, displayName = name, email = "$uid@example.com", createdAt = SENT_AT)

  private companion object {
    const val EVENT = "event-1"
    const val TEAM = "team-1"
    const val ME = "me"
    const val OTHER = "alice"
    const val NAMELESS = "nameless"
    val SENT_AT: Instant = Instant.parse("2027-06-20T09:30:00Z")
  }
}
