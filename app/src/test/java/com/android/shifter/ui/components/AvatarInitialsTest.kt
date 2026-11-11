// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarInitialsTest {
  @Test
  fun avatarInitials_withMissingOrBlankName_returnsQuestionMark() {
    listOf(null, "", " ", "\t\n").forEach { assertEquals("?", avatarInitials(it)) }
  }

  @Test
  fun avatarInitials_withWhitespace_usesTheFirstTwoWords() {
    assertEquals("BR", avatarInitials("  bob \t rossi  junior "))
    assertEquals("BR", avatarInitials("bob\u00a0rossi"))
    assertEquals("B", avatarInitials(" bob "))
  }

  @Test
  fun avatarInitials_withSupplementaryCharacters_keepsWholeCodePoints() {
    assertEquals("😀S", avatarInitials("😀 Sam"))
    assertEquals("𐐀S", avatarInitials("𐐨 Sam"))
  }

  @Test
  fun avatarInitials_withSingleInitialLimit_returnsOneInitial() {
    assertEquals("B", avatarInitials("bob rossi", maxInitials = 1))
    assertEquals("😀", avatarInitials("😀 Sam", maxInitials = 1))
  }
}
