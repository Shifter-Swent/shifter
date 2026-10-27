// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Leaves room for a slow CI emulator, while still failing a call that hangs. */
private const val EMULATOR_TEST_TIMEOUT_MILLIS = 30_000L

/**
 * Runs [block] against the Firebase emulators, failing the test instead of hanging when the
 * emulators cannot be reached.
 *
 * The failure names the [step] that did not complete (sign-in, write, read, ...), so a timeout on
 * CI tells a stuck call from a slow test. It is never retried: that would hide the problem.
 */
fun emulatorTest(
    timeoutMillis: Long = EMULATOR_TEST_TIMEOUT_MILLIS,
    block: suspend () -> Unit,
) {
  val steps = Steps()
  runBlocking(steps) {
    // Null only on this timeout: a `withTimeout` inside [block] fails the test with its own.
    withTimeoutOrNull(timeoutMillis) {
      block()
      true
    } ?: throw AssertionError("Timed out after $timeoutMillis ms ${steps.current}")
  }
}

/**
 * Runs [block] as the step called [name], the one an [emulatorTest] timeout reports.
 *
 * An [emulatorTest] remembers a single current step, so steps run in parallel (`async`) may report
 * the wrong one.
 */
suspend fun <T> step(name: String, block: suspend () -> T): T {
  val steps = currentCoroutineContext()[Steps] ?: return block()
  val previous = steps.current
  steps.current = "in step '$name'"
  val result =
      try {
        block()
      } catch (e: Throwable) {
        // A cancelled step is the one the timeout interrupted: keep its name for the report.
        if (e !is CancellationException) steps.current = previous
        throw e
      }
  steps.current = previous
  return result
}

/** Remembers which step an [emulatorTest] is currently waiting on. */
private class Steps : AbstractCoroutineContextElement(Steps) {
  companion object Key : CoroutineContext.Key<Steps>

  var current = "outside any named step"
}
