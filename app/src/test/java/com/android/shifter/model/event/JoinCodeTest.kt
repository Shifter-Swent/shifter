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
  fun normalize_keepsOnlyAsciiLettersAndDigits() {
    assertEquals("LVE", JoinCode.normalize("ÉLÈVEß١２"))
  }

  @Test
  fun normalize_keepsCharactersNoCodeUses() {
    assertEquals("ABCDE0", JoinCode.normalize("abcde0"))
  }

  @Test
  fun isValid_onlyForACompleteCodeOfUsedCharacters() {
    assertTrue(JoinCode.isValid("ABC234"))
    assertFalse(JoinCode.isValid("ABC23"))
    assertFalse(JoinCode.isValid("ABCDE0"))
    assertFalse(JoinCode.isValid("ABCDEI"))
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
