// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatMessageTest {

  @Test
  fun chatMessageText_trimsTheText() {
    assertEquals("Hello team", chatMessageText("  Hello team\n"))
  }

  @Test
  fun chatMessageText_withOneCharacter_isAccepted() {
    assertEquals("a", chatMessageText("a"))
  }

  @Test
  fun chatMessageText_withTheMaximumLength_isAccepted() {
    val longest = "a".repeat(ChatRules.MAX_TEXT_LENGTH)

    assertEquals(longest, chatMessageText(longest))
  }

  @Test
  fun chatMessageText_withTheMaximumLengthOnceTrimmed_isAccepted() {
    val longest = "a".repeat(ChatRules.MAX_TEXT_LENGTH)

    assertEquals(longest, chatMessageText(" $longest "))
  }

  @Test
  fun chatMessageText_tooLong_throws() {
    assertThrows(IllegalArgumentException::class.java) {
      chatMessageText("a".repeat(ChatRules.MAX_TEXT_LENGTH + 1))
    }
  }

  @Test
  fun chatMessageText_empty_throws() {
    assertThrows(IllegalArgumentException::class.java) { chatMessageText("") }
  }

  @Test
  fun chatMessageText_blank_throws() {
    assertThrows(IllegalArgumentException::class.java) { chatMessageText(" \n\t ") }
  }
}
