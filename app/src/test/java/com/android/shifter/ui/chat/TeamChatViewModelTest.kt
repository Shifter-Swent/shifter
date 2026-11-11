// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import com.swent.shifter.model.chat.ChatChannelId
import com.swent.shifter.model.chat.ChatMessage
import com.swent.shifter.model.chat.ChatRepositoryException
import com.swent.shifter.model.chat.ChatRules
import com.swent.shifter.model.chat.FakeChatRepository
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.PublicProfile
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.UserRepositoryException
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

  private val chat = FakeChatRepository(currentUserId = ME, clock = { SENT_AT.plusSeconds(60) })
  private val users =
      FakeUserRepository(listOf(user(ME, "Liam"), user(OTHER, "Alice"), user(NAMELESS, " ")))

  private val profiles = CountingUserRepository(users)

  private fun viewModel() = TeamChatViewModel(EVENT, TEAM, chat, profiles) { ME }

  private fun receive(id: String, from: String, text: String) =
      chat.add(ChatChannelId.Team(EVENT, TEAM), ChatMessage(id, from, text, SENT_AT))

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
    users.failure = UserRepositoryException.Unavailable()
    receive("1", OTHER, "Hello")

    val state = viewModel().uiState.value

    assertEquals(listOf("Hello"), state.messages.map { it.text })
    assertNull(state.messages.single().senderName)
  }

  @Test
  fun messages_whileANameIsLoading_stillShowUpInRealTime() = runTest {
    val release = CompletableDeferred<Unit>()
    profiles.release = release
    val viewModel = viewModel()

    receive("1", OTHER, "Hello")
    receive("2", OTHER, "Anyone at gate B?")

    val messages = viewModel.uiState.value.messages
    assertEquals(listOf("Hello", "Anyone at gate B?"), messages.map { it.text })
    assertEquals(listOf(null, null), messages.map { it.senderName })

    release.complete(Unit)

    assertEquals(listOf("Alice", "Alice"), viewModel.uiState.value.messages.map { it.senderName })
  }

  @Test
  fun messages_whenANameFails_readItAgainOnlyOnTheNextLoad() = runTest {
    users.failure = UserRepositoryException.Unavailable()
    receive("1", OTHER, "Hello")
    val viewModel = viewModel()

    receive("2", OTHER, "Anyone at gate B?")

    assertEquals(1, profiles.reads)

    users.failure = null
    viewModel.load()

    assertEquals(2, profiles.reads)
    assertEquals(listOf("Alice", "Alice"), viewModel.uiState.value.messages.map { it.senderName })
  }

  @Test
  fun load_whenUnavailable_showsOffline() = runTest {
    chat.failure = ChatRepositoryException.Unavailable()

    val state = viewModel().uiState.value

    assertFalse(state.isLoading)
    assertEquals(ChatError.OFFLINE, state.error)
  }

  @Test
  fun load_whenItFails_showsTheUnexpectedError() = runTest {
    chat.failure = ChatRepositoryException.Unknown()

    assertEquals(ChatError.UNEXPECTED, viewModel().uiState.value.error)
  }

  @Test
  fun load_whenNotAllowed_showsNotAllowed() = runTest {
    chat.failure = ChatRepositoryException.NotAllowed()

    assertEquals(ChatError.NOT_ALLOWED, viewModel().uiState.value.error)
  }

  @Test
  fun messages_whenAccessIsLost_hideTheMessagesAndShowNotAllowed() = runTest {
    receive("1", OTHER, "Hello")
    val viewModel = viewModel()

    chat.failure = ChatRepositoryException.NotAllowed()

    val state = viewModel.uiState.value
    assertEquals(ChatError.NOT_ALLOWED, state.error)
    assertTrue(state.messages.isEmpty())
  }

  @Test
  fun messages_whenAccessIsLost_hideThePendingMessagesAndBlockSending() = runTest {
    chat.confirmation = CompletableDeferred()
    val viewModel = viewModel()
    viewModel.onDraftChange("On my way")
    viewModel.send()

    chat.failure = ChatRepositoryException.NotAllowed()
    viewModel.onDraftChange("Hello?")

    val state = viewModel.uiState.value
    assertTrue(state.messages.isEmpty())
    assertFalse(state.canSend)
  }

  @Test
  fun load_afterAnError_showsTheMessages() = runTest {
    chat.failure = ChatRepositoryException.Unavailable()
    val viewModel = viewModel()
    chat.failure = null
    receive("1", OTHER, "Hello")

    viewModel.load()

    assertNull(viewModel.uiState.value.error)
    assertEquals(listOf("Hello"), viewModel.uiState.value.messages.map { it.text })
  }

  @Test
  fun canSend_withBlankOrTooLongText_isFalse() {
    val listed = TeamChatUiState(isLoading = false)
    assertFalse(listed.copy(draft = "   ").canSend)
    assertFalse(listed.copy(draft = "a".repeat(ChatRules.MAX_TEXT_LENGTH + 1)).canSend)
    assertTrue(listed.copy(draft = " ${"a".repeat(ChatRules.MAX_TEXT_LENGTH)} ").canSend)
  }

  @Test
  fun canSend_whileLoadingOrNotAllowed_isFalse() {
    assertFalse(TeamChatUiState(draft = "Hi", isLoading = true).canSend)
    assertFalse(
        TeamChatUiState(draft = "Hi", isLoading = false, error = ChatError.NOT_ALLOWED).canSend
    )
    assertTrue(TeamChatUiState(draft = "Hi", isLoading = false, error = ChatError.OFFLINE).canSend)
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

    confirmation.completeExceptionally(ChatRepositoryException.Unavailable())

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

    confirmation.completeExceptionally(ChatRepositoryException.NotAllowed())

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
    chat.failure = ChatRepositoryException.Unknown()
    viewModel.onDraftChange("On my way")

    viewModel.send()

    val state = viewModel.uiState.value
    assertEquals("On my way", state.draft)
    assertTrue(state.sendFailed)
    assertTrue(state.messages.isEmpty())
  }

  @Test
  fun send_whenItFailsWithABlankDraft_givesTheTextBack() = runTest {
    val confirmation = CompletableDeferred<Unit>()
    chat.confirmation = confirmation
    val viewModel = viewModel()
    viewModel.onDraftChange("On my way")
    viewModel.send()
    viewModel.onDraftChange(" ")

    confirmation.completeExceptionally(ChatRepositoryException.NotAllowed())

    assertEquals("On my way", viewModel.uiState.value.draft)
  }

  @Test
  fun onDraftChange_clearsTheSendFailure() = runTest {
    val viewModel = viewModel()
    chat.failure = ChatRepositoryException.Unknown()
    viewModel.onDraftChange("On my way")
    viewModel.send()

    viewModel.onDraftChange("On my way!")

    assertFalse(viewModel.uiState.value.sendFailed)
  }

  private fun user(uid: String, name: String) =
      User(uid = uid, displayName = name, email = "$uid@example.com", createdAt = SENT_AT)

  /** Counts the profile reads, and holds each one until [release] completes, when set. */
  private class CountingUserRepository(private val users: FakeUserRepository) :
      UserRepository by users {
    var reads = 0
    var release: CompletableDeferred<Unit>? = null

    override suspend fun getPublicProfile(uid: String): PublicProfile? {
      reads++
      release?.await()
      return users.getPublicProfile(uid)
    }
  }

  private companion object {
    const val EVENT = "event-1"
    const val TEAM = "team-1"
    const val ME = "me"
    const val OTHER = "alice"
    const val NAMELESS = "nameless"
    val SENT_AT: Instant = Instant.parse("2027-06-20T09:30:00Z")
  }
}
