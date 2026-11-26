// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TeamChatContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val drafts = mutableListOf<String>()
  private var sends = 0
  private var retries = 0
  private val announcementChanges = mutableListOf<Boolean>()

  private val state = mutableStateOf(TeamChatUiState())

  /** Shows [initial], which the test can then change through [state]. */
  private fun setContent(initial: TeamChatUiState) {
    state.value = initial
    composeTestRule.setContent {
      ShifterTheme {
        TeamChatContent(
            state = state.value,
            onDraftChange = { drafts += it },
            onSend = { sends++ },
            onRetry = { retries++ },
            onAnnouncementChange = { announcementChanges += it },
            zone = ZoneOffset.UTC,
            today = TODAY,
        )
      }
    }
  }

  private fun tag(tag: String) = composeTestRule.onNodeWithTag(tag)

  @Test
  fun loading_showsTheSpinner() {
    setContent(TeamChatUiState(isLoading = true))

    tag(TeamChatTestTags.LOADING).assertIsDisplayed()
  }

  @Test
  fun emptyChannel_showsNoMessagesYet() {
    setContent(TeamChatUiState(isLoading = false))

    tag(TeamChatTestTags.EMPTY).assertIsDisplayed()
  }

  @Test
  fun error_retry_callsOnRetry() {
    setContent(TeamChatUiState(isLoading = false, error = ChatError.OFFLINE))

    tag(TeamChatTestTags.ERROR).assertIsDisplayed()
    tag(TeamChatTestTags.RETRY_BUTTON).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun notAllowed_showsTheErrorWithoutRetry() {
    setContent(TeamChatUiState(isLoading = false, error = ChatError.NOT_ALLOWED))

    tag(TeamChatTestTags.ERROR).assert(hasText("no longer have access", substring = true))
    tag(TeamChatTestTags.RETRY_BUTTON).assertDoesNotExist()
  }

  @Test
  fun messages_showTheSenderTheTextAndTheTime() {
    setContent(TeamChatUiState(isLoading = false, messages = listOf(FROM_ALICE, UNNAMED, MINE)))

    tag(TeamChatTestTags.message("1")).assert(hasText("Alice", substring = true))
    tag(TeamChatTestTags.message("1")).assert(hasText("Hello", substring = true))
    tag(TeamChatTestTags.message("1")).assert(hasText("09:30", substring = true))
    tag(TeamChatTestTags.message("2")).assert(hasText("Unnamed member", substring = true))
    tag(TeamChatTestTags.message("3")).assert(hasText("Hi", substring = true))
    tag(TeamChatTestTags.message("3")).assert(hasText("Liam", substring = true).not())
  }

  @Test
  fun messagesOfSeveralDays_areSplitByTheirDayChips() {
    val older = FROM_ALICE.copy(id = "0", sentAt = AT.minusSeconds(3 * 86_400))
    val yesterday = FROM_ALICE.copy(id = "5", sentAt = AT.minusSeconds(86_400))
    val lastYear = FROM_ALICE.copy(id = "6", sentAt = AT.minusSeconds(400 * 86_400))
    setContent(
        TeamChatUiState(
            isLoading = false,
            messages = listOf(lastYear, older, yesterday, FROM_ALICE),
        )
    )

    tag(TeamChatTestTags.day(TODAY)).assert(hasText("Today"))
    tag(TeamChatTestTags.day(TODAY.minusDays(1))).assert(hasText("Yesterday"))
    tag(TeamChatTestTags.day(TODAY.minusDays(3))).assert(hasText("17 Jun"))
    tag(TeamChatTestTags.day(TODAY.minusDays(400))).assert(hasText("16 May 2026"))
  }

  @Test
  fun longRunOfAnEarlierDay_keepsItsChipOnScreenAtTheBottom() {
    val earlier = numbered(0..39).map { it.copy(sentAt = AT.minusSeconds(3 * 86_400)) }
    setContent(TeamChatUiState(isLoading = false, messages = earlier))

    // The list opens on the last message, far below the first one of that day.
    tag(TeamChatTestTags.message("39")).assertIsDisplayed()
    tag(TeamChatTestTags.message("0")).assertDoesNotExist()
    tag(TeamChatTestTags.day(TODAY.minusDays(3))).assertIsDisplayed()
  }

  @Test
  fun messageOfAnotherDay_showsOnlyItsTime() {
    val yesterday = FROM_ALICE.copy(sentAt = AT.minusSeconds(86_400))
    setContent(TeamChatUiState(isLoading = false, messages = listOf(yesterday)))

    tag(TeamChatTestTags.message("1")).assert(hasText("09:30"))
    tag(TeamChatTestTags.message("1")).assert(hasText("Jun", substring = true).not())
  }

  @Test
  fun group_showsTheNameOnlyOnItsFirstBubble() {
    setContent(TeamChatUiState(isLoading = false, messages = numbered(0..2)))

    tag(TeamChatTestTags.message("0")).assert(hasText("Alice", substring = true))
    tag(TeamChatTestTags.message("1")).assert(hasText("Alice", substring = true).not())
    tag(TeamChatTestTags.message("2")).assert(hasText("Alice", substring = true).not())
  }

  @Test
  fun group_namesTheSenderToTalkBackOnEachBubble() {
    setContent(TeamChatUiState(isLoading = false, messages = numbered(0..2)))

    // The first bubble reads its visible name, so it is not described a second time.
    tag(TeamChatTestTags.message("0")).assert(hasContentDescription("Alice").not())
    tag(TeamChatTestTags.message("1")).assert(hasContentDescription("Alice"))
    tag(TeamChatTestTags.message("2")).assert(hasContentDescription("Alice"))
  }

  @Test
  fun ownMessage_isDescribedAsYoursToTalkBack() {
    setContent(TeamChatUiState(isLoading = false, messages = listOf(MINE)))

    tag(TeamChatTestTags.message("3")).assert(hasContentDescription("You"))
  }

  @Test
  fun group_showsTheTimeOnlyOnItsLastBubble() {
    setContent(TeamChatUiState(isLoading = false, messages = numbered(0..2)))

    tag(TeamChatTestTags.message("0")).assert(hasText("09:30", substring = true).not())
    tag(TeamChatTestTags.message("1")).assert(hasText("09:30", substring = true).not())
    tag(TeamChatTestTags.message("2")).assert(hasText("09:30", substring = true))
  }

  @Test
  fun sameName_fromAnotherMember_startsAGroup() {
    val other = FROM_ALICE.copy(id = "5", senderId = "alice2")
    setContent(TeamChatUiState(isLoading = false, messages = listOf(FROM_ALICE, other)))

    tag(TeamChatTestTags.message("5")).assert(hasText("Alice", substring = true))
  }

  @Test
  fun pendingMessage_showsSending() {
    setContent(
        TeamChatUiState(
            isLoading = false,
            messages = listOf(MINE.copy(id = "4", sentAt = null, isPending = true)),
        )
    )

    // Inside the message, which is read as one node.
    composeTestRule
        .onNodeWithTag(TeamChatTestTags.pending("4"), useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun send_withBlankDraft_isDisabled() {
    setContent(TeamChatUiState(isLoading = false, draft = "  "))

    tag(TeamChatTestTags.SEND_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun send_whenAccessIsLost_isDisabled() {
    setContent(
        TeamChatUiState(isLoading = false, error = ChatError.NOT_ALLOWED, draft = "On my way")
    )

    tag(TeamChatTestTags.SEND_BUTTON).assertIsNotEnabled()
  }

  @Test
  fun send_withADraft_callsOnSend() {
    setContent(TeamChatUiState(isLoading = false, draft = "On my way"))

    tag(TeamChatTestTags.SEND_BUTTON).assertIsEnabled().performClick()

    assertEquals(1, sends)
  }

  @Test
  fun typing_callsOnDraftChange() {
    setContent(TeamChatUiState(isLoading = false))

    tag(TeamChatTestTags.INPUT).performTextInput("Hi")

    assertEquals(listOf("Hi"), drafts)
  }

  @Test
  fun sendFailed_showsTheErrorAndKeepsTheDraft() {
    setContent(TeamChatUiState(isLoading = false, draft = "Hi", sendFailed = true))

    tag(TeamChatTestTags.SEND_FAILED).assertIsDisplayed()
    tag(TeamChatTestTags.ANNOUNCEMENT_REFUSED).assertDoesNotExist()
    tag(TeamChatTestTags.INPUT).assert(hasText("Hi"))
  }

  @Test
  fun error_withMessagesShown_keepsThemAndOffersRetry() {
    setContent(TeamChatUiState(isLoading = false, error = ChatError.OFFLINE, messages = SHOWN))

    tag(TeamChatTestTags.message("1")).assertIsDisplayed()
    tag(TeamChatTestTags.ERROR).assertIsDisplayed()
    tag(TeamChatTestTags.RETRY_BUTTON).performClick()

    assertEquals(1, retries)
  }

  @Test
  fun reload_withMessagesShown_keepsThemInsteadOfTheSpinner() {
    setContent(TeamChatUiState(isLoading = true, messages = SHOWN))

    tag(TeamChatTestTags.message("1")).assertIsDisplayed()
    tag(TeamChatTestTags.LOADING).assertDoesNotExist()
  }

  @Test
  fun notAllowed_withMessages_hidesThem() {
    setContent(TeamChatUiState(isLoading = false, error = ChatError.NOT_ALLOWED, messages = SHOWN))

    tag(TeamChatTestTags.ERROR).assertIsDisplayed()
    tag(TeamChatTestTags.message("1")).assertDoesNotExist()
  }

  @Test
  fun newMessage_inAFullChannel_isFollowed() {
    val full = numbered(0 until 100)
    setContent(TeamChatUiState(isLoading = false, messages = full))

    // The oldest leaves as the newest arrives: the count stays the same.
    state.value = state.value.copy(messages = full.drop(1) + numbered(100..100))

    tag(TeamChatTestTags.message("100")).assertIsDisplayed()
  }

  @Test
  fun newMessage_whileReadingOlderOnes_doesNotScroll() {
    val messages = numbered(0 until 50)
    setContent(TeamChatUiState(isLoading = false, messages = messages))
    tag(TeamChatTestTags.MESSAGES).performScrollToIndex(0)

    state.value = state.value.copy(messages = messages + numbered(50..50))

    tag(TeamChatTestTags.message("0")).assertIsDisplayed()
    tag(TeamChatTestTags.message("50")).assertIsNotDisplayed()
  }

  @Test
  fun ownMessage_whileReadingOlderOnes_isFollowed() {
    val messages = numbered(0 until 50)
    setContent(TeamChatUiState(isLoading = false, messages = messages))
    tag(TeamChatTestTags.MESSAGES).performScrollToIndex(0)

    state.value =
        state.value.copy(
            messages = messages + MINE.copy(id = "mine", sentAt = null, isPending = true)
        )

    tag(TeamChatTestTags.message("mine")).assertIsDisplayed()
  }

  @Test
  fun announcement_showsTheCardWithItsAuthor() {
    setContent(TeamChatUiState(isLoading = false, messages = listOf(ANNOUNCEMENT, FROM_ALICE)))

    composeTestRule
        .onNodeWithTag(TeamChatTestTags.announcementTag(ANNOUNCEMENT.id), useUnmergedTree = true)
        .assertIsDisplayed()
        .assert(hasAnyChild(hasText("Alice")))
    tag(TeamChatTestTags.message(ANNOUNCEMENT.id))
        .assert(hasContentDescription("Announcement"))
        .assert(hasText("Gates open at 9:00"))
        .assert(hasText("09:30"))
  }

  @Test
  fun announcement_isWiderThanABubble() {
    // A text long enough for the bubble to reach its widest.
    val long = MINE.copy(id = "long", text = "Gates open at 9:00. ".repeat(10))
    setContent(TeamChatUiState(isLoading = false, messages = listOf(ANNOUNCEMENT, long)))

    val card = tag(TeamChatTestTags.message(ANNOUNCEMENT.id)).getBoundsInRoot()
    val bubble = tag(TeamChatTestTags.message(long.id)).getBoundsInRoot()
    assertTrue(card.width > bubble.width)
  }

  @Test
  fun ownAnnouncement_sitsOnTheRight() {
    val mine = ANNOUNCEMENT.copy(id = "5", isMine = true)
    setContent(TeamChatUiState(isLoading = false, messages = listOf(ANNOUNCEMENT, mine)))

    val theirs = tag(TeamChatTestTags.message(ANNOUNCEMENT.id)).getBoundsInRoot()
    val own = tag(TeamChatTestTags.message(mine.id)).getBoundsInRoot()
    // Both end at the same edge; the user's own leaves room free at the start.
    assertEquals(theirs.right, own.right)
    assertTrue(own.left > theirs.left)
  }

  @Test
  fun ownAnnouncement_showsYouOnTheCard() {
    val mine = ANNOUNCEMENT.copy(isMine = true, sentAt = null, isPending = true)
    setContent(TeamChatUiState(isLoading = false, messages = listOf(mine)))

    composeTestRule
        .onNodeWithTag(TeamChatTestTags.announcementTag(mine.id), useUnmergedTree = true)
        .assert(hasAnyChild(hasText("You")))
    composeTestRule
        .onNodeWithTag(TeamChatTestTags.pending(mine.id), useUnmergedTree = true)
        .assertIsDisplayed()
  }

  @Test
  fun normalMessage_hasNoAnnouncementTag() {
    setContent(TeamChatUiState(isLoading = false, messages = listOf(FROM_ALICE)))

    composeTestRule
        .onNodeWithTag(TeamChatTestTags.announcementTag(FROM_ALICE.id), useUnmergedTree = true)
        .assertDoesNotExist()
  }

  @Test
  fun announcementOption_whenNotAllowed_isHidden() {
    setContent(TeamChatUiState(isLoading = false))

    tag(TeamChatTestTags.ANNOUNCEMENT_TOGGLE).assertDoesNotExist()
  }

  @Test
  fun announcementOption_whenNotAllowed_keepsTheMessagePlaceholder() {
    setContent(TeamChatUiState(isLoading = false, asAnnouncement = true))

    tag(TeamChatTestTags.INPUT).assert(hasText("Write a message"))
  }

  @Test
  fun announcementOption_click_callsOnAnnouncementChange() {
    setContent(TeamChatUiState(isLoading = false, canAnnounce = true))

    tag(TeamChatTestTags.ANNOUNCEMENT_TOGGLE).assertIsOff().performClick()

    assertEquals(listOf(true), announcementChanges)
  }

  @Test
  fun announcementOption_whenOn_isCheckedAndChangesThePlaceholder() {
    setContent(TeamChatUiState(isLoading = false, canAnnounce = true, asAnnouncement = true))

    tag(TeamChatTestTags.ANNOUNCEMENT_TOGGLE).assertIsOn()
    tag(TeamChatTestTags.INPUT).assert(hasText("Write an announcement"))
  }

  @Test
  fun announcementRefused_showsTheErrorAndKeepsTheDraft() {
    setContent(
        TeamChatUiState(
            isLoading = false,
            draft = "Gates open at 9:00",
            canAnnounce = true,
            asAnnouncement = true,
            announcementRefused = true,
        )
    )

    tag(TeamChatTestTags.ANNOUNCEMENT_REFUSED).assertIsDisplayed()
    tag(TeamChatTestTags.SEND_FAILED).assertDoesNotExist()
    tag(TeamChatTestTags.INPUT).assert(hasText("Gates open at 9:00"))
  }

  @Test
  fun initialOf_takesTheFirstCodePoint() {
    assertEquals("A", initialOf("  alice "))
    assertEquals("\uD83D\uDE00", initialOf("\uD83D\uDE00 Smiley"))
    assertEquals(null, initialOf(" "))
    assertEquals(null, initialOf(null))
  }

  private fun numbered(ids: IntRange) = ids.map {
    ChatMessageItem(
        "$it",
        "Message $it",
        senderId = "alice",
        senderName = "Alice",
        AT,
        isMine = false,
    )
  }

  private companion object {
    val AT: Instant = Instant.parse("2027-06-20T09:30:00Z")
    val TODAY: LocalDate = LocalDate.of(2027, 6, 20)
    val FROM_ALICE =
        ChatMessageItem("1", "Hello", senderId = "alice", senderName = "Alice", AT, isMine = false)
    val UNNAMED =
        ChatMessageItem("2", "Hey", senderId = "unnamed", senderName = null, AT, isMine = false)
    val MINE = ChatMessageItem("3", "Hi", senderId = "liam", senderName = "Liam", AT, isMine = true)
    val ANNOUNCEMENT =
        ChatMessageItem(
            "4",
            "Gates open at 9:00",
            senderId = "alice",
            senderName = "Alice",
            AT,
            isMine = false,
            isAnnouncement = true,
        )
    val SHOWN = listOf(FROM_ALICE)
  }
}
