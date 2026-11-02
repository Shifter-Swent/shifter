// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.event

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinCodeTest {

  @Test
  fun normalize_upperCasesTheCode() {
    assertEquals("ABC234", JoinCode.normalize("abc234"))
  }

  @Test
  fun normalize_dropsSpacesAndDashes() {
    assertEquals("ABC234", JoinCode.normalize(" abc-234 "))
  }

  @Test
  fun normalize_cutsTheCodeToItsLength() {
    assertEquals("ABC234", JoinCode.normalize("ABC234XYZ"))
  }

  @Test
  fun isComplete_onlyWithEveryCharacter() {
    assertFalse(JoinCode.isComplete("ABC23"))
    assertTrue(JoinCode.isComplete("ABC234"))
  }

  @Test
  fun alphabet_hasNoConfusingCharacter() {
    assertTrue("0O1IL".none(JoinCode.ALPHABET::contains))
  }
}
