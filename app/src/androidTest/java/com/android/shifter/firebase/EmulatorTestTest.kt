// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that [emulatorTest] reports the [step] a timeout interrupted. It needs no emulator. */
@RunWith(AndroidJUnit4::class)
class EmulatorTestTest {

  @Test
  fun emulatorTest_whenAStepHangs_namesThatStep() {
    val failure =
        assertThrows(AssertionError::class.java) {
          emulatorTest(TIMEOUT_MILLIS) {
            step("sign-in") {}
            step("write") { awaitCancellation() }
          }
        }

    assertEquals("Timed out after $TIMEOUT_MILLIS ms in step 'write'", failure.message)
  }

  @Test
  fun emulatorTest_whenANestedStepHangs_namesTheInnermostStep() {
    val failure =
        assertThrows(AssertionError::class.java) {
          emulatorTest(TIMEOUT_MILLIS) { step("create") { step("read") { awaitCancellation() } } }
        }

    assertEquals("Timed out after $TIMEOUT_MILLIS ms in step 'read'", failure.message)
  }

  @Test
  fun emulatorTest_whenItHangsAfterAFailedStep_doesNotBlameThatStep() {
    val failure =
        assertThrows(AssertionError::class.java) {
          emulatorTest(TIMEOUT_MILLIS) {
            runCatching { step("denied write") { error("permission denied") } }
            awaitCancellation()
          }
        }

    assertEquals("Timed out after $TIMEOUT_MILLIS ms outside any named step", failure.message)
  }

  @Test
  fun step_returnsTheResultOfItsBlock() {
    var result = 0

    emulatorTest(TIMEOUT_MILLIS) { result = step("read") { 42 } }

    assertEquals(42, result)
  }

  private companion object {
    const val TIMEOUT_MILLIS = 100L
  }
}
