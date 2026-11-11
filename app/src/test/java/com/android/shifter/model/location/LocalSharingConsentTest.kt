// Co-authored-by: Claude Opus 4.8 <noreply@anthropic.com>
package com.swent.shifter.model.location

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [LocalSharingConsent], the in-memory decision shared between Settings and the foreground
 * publisher. Unconfined, so each change reaches the collector rather than being conflated.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocalSharingConsentTest {

  private val consent = LocalSharingConsent()

  @Test
  fun applyTo_withoutADecision_forwardsTheRealConsent() =
      runTest(UnconfinedTestDispatcher()) {
        val real = MutableStateFlow(true)
        val seen = mutableListOf<Boolean>()
        val job = backgroundScope.launch { consent.applyTo(real, USER).toList(seen) }

        real.value = false
        real.value = true
        job.cancel()

        assertEquals(listOf(true, false, true), seen)
      }

  @Test
  fun applyTo_whileTheRealConsentLoads_emitsNothing_evenOnceForcedOff() =
      runTest(UnconfinedTestDispatcher()) {
        // A real consent that has not emitted yet, as while the profile first loads.
        val loading = MutableSharedFlow<Boolean>()
        val seen = mutableListOf<Boolean>()
        val job = backgroundScope.launch { consent.applyTo(loading, USER).toList(seen) }

        // Forcing off alone must not produce a leading false before the real consent is known.
        consent.turnOff(USER)
        runCurrent()
        job.cancel()

        assertEquals(emptyList<Boolean>(), seen)
      }

  @Test
  fun turnOff_forcesOffAtOnce_withoutWaitingForTheRealConsent() =
      runTest(UnconfinedTestDispatcher()) {
        val real = MutableStateFlow(true)
        val seen = mutableListOf<Boolean>()
        val job = backgroundScope.launch { consent.applyTo(real, USER).toList(seen) }

        // The real consent never changes here: the off comes from the local decision alone.
        consent.turnOff(USER)
        job.cancel()

        assertEquals(listOf(true, false), seen)
      }

  @Test
  fun applyTo_releasesTheDecision_onceTheRealConsentConfirmsTheOff() =
      runTest(UnconfinedTestDispatcher()) {
        val real = MutableStateFlow(true)
        consent.turnOff(USER)
        val seen = mutableListOf<Boolean>()
        val job = backgroundScope.launch { consent.applyTo(real, USER).toList(seen) }

        // The real consent catches up to the off: the decision is reconciled and dropped.
        real.value = false
        // A later on, e.g. from another device, is then followed rather than held off.
        real.value = true
        job.cancel()

        assertEquals(listOf(false, true), seen)
      }

  @Test
  fun applyTo_doesNotReleaseTheDecision_whileTheRealConsentStillSaysOn() =
      runTest(UnconfinedTestDispatcher()) {
        val real = MutableStateFlow(true)
        consent.turnOff(USER)
        val seen = mutableListOf<Boolean>()
        val job = backgroundScope.launch { consent.applyTo(real, USER).toList(seen) }

        // The real consent is still the stale on: the forced off must not be dropped onto it, or
        // GPS would resume from an older snapshot.
        real.value = true
        job.cancel()

        assertEquals(listOf(false), seen)
      }

  @Test
  fun release_dropsTheDecision_soTheRealConsentDrivesAgain() =
      runTest(UnconfinedTestDispatcher()) {
        val real = MutableStateFlow(true)
        consent.turnOff(USER)
        val seen = mutableListOf<Boolean>()
        val job = backgroundScope.launch { consent.applyTo(real, USER).toList(seen) }

        // Turning sharing back on, or rolling back a failed save, releases the decision: the real
        // consent (still on) drives again.
        consent.release(USER)
        job.cancel()

        assertEquals(listOf(false, true), seen)
      }

  @Test
  fun turnOff_isScopedToItsUser() =
      runTest(UnconfinedTestDispatcher()) {
        val real = MutableStateFlow(true)
        val seen = mutableListOf<Boolean>()
        // Another account is signed in on the publisher side.
        val job = backgroundScope.launch { consent.applyTo(real, OTHER_USER).toList(seen) }

        // One user's off decision must not suppress another user's sharing.
        consent.turnOff(USER)
        job.cancel()

        assertEquals(listOf(true), seen)
      }

  private companion object {
    const val USER = "user-1"
    const val OTHER_USER = "user-2"
  }
}
