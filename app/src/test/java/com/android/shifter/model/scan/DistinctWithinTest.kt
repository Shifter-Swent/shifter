// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.scan

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DistinctWithinTest {

  /** A camera that sees each text of [frames] after the delay before it, in virtual time. */
  private fun frames(vararg frames: Pair<Long, String>): Flow<String> = flow {
    frames.forEach { (delayMillis, text) ->
      delay(delayMillis)
      emit(text)
    }
  }

  private suspend fun TestScope.filter(source: Flow<String>): List<String> =
      source.distinctWithin(WINDOW) { testScheduler.currentTime }.toList()

  @Test
  fun distinctWithin_aCodeHeldInView_isEmittedOnce() = runTest {
    // Seen every 100 ms for 5 s: always less than the window since it was last seen.
    val held = frames(*Array(50) { 100L to CODE })

    assertEquals(listOf(CODE), filter(held))
  }

  @Test
  fun distinctWithin_aCodeBackAfterTheWindow_isEmittedAgain() = runTest {
    val source = frames(0L to CODE, WINDOW to CODE)

    assertEquals(listOf(CODE, CODE), filter(source))
  }

  @Test
  fun distinctWithin_aCodeBackWithinTheWindow_isDropped() = runTest {
    val source = frames(0L to CODE, WINDOW - 1 to CODE)

    assertEquals(listOf(CODE), filter(source))
  }

  @Test
  fun distinctWithin_anotherCode_passesRightAway() = runTest {
    val source = frames(0L to CODE, 10L to OTHER, 10L to CODE)

    assertEquals(listOf(CODE, OTHER), filter(source))
  }

  private companion object {
    const val WINDOW = 2_000L
    const val CODE = "AB23CD"
    const val OTHER = "EF45GH"
  }
}
