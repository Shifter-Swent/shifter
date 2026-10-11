// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import com.swent.shifter.model.authentication.AuthUser
import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarInitialTest {

  @Test
  fun avatarInitial_isTheUppercaseFirstLetterOfTheName() {
    assertEquals("A", avatarInitial(AuthUser("uid", "zoe@example.com", " ada lovelace")))
  }

  @Test
  fun avatarInitial_fallsBackToTheEmailWithoutAName() {
    assertEquals("Z", avatarInitial(AuthUser("uid", "zoe@example.com", null)))
    assertEquals("Z", avatarInitial(AuthUser("uid", "zoe@example.com", "  ")))
  }

  @Test
  fun avatarInitial_isAQuestionMarkWithoutNameOrEmail() {
    assertEquals("?", avatarInitial(AuthUser("uid", null, null)))
    assertEquals("?", avatarInitial(null))
  }
}
