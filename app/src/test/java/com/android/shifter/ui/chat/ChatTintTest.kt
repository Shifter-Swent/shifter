// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTintTest {

  // "polygenelubricants" hashes to Int.MIN_VALUE, the edge case of a negative hash.
  private val names =
      listOf("Alice", "Bob", "Chloé", "team_42", "", "Ünïcødé 🎉", "polygenelubricants")

  @Test
  fun chatTint_sameName_givesTheSameTint() {
    names.forEach { assertEquals(it, chatTint(it), chatTint(String(it.toCharArray()))) }
  }

  @Test
  fun chatTint_isOneOfTheChatTints() {
    names.forEach { assertTrue(it, chatTint(it) in CHAT_TINTS) }
  }
}
