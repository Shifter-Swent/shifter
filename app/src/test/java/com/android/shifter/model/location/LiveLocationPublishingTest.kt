// Co-authored-by: Claude Opus 4.8 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserRepositoryException
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [sharingConsent] and [runLiveLocationPublishing], the glue between the consent observation and
 * the coordinator, with virtual time and a double for the sessions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveLocationPublishingTest {

  // ── sharingConsent ──

  @Test
  fun sharingConsent_emitsTheRealSettingFirst_notAPlaceholder() = runTest {
    val repository =
        FakeUserRepository(listOf(User(USER, "Ada", "a@b.c", locationSharingEnabled = true)))
    val seen = mutableListOf<Boolean>()
    val job = backgroundScope.launch { repository.sharingConsent(USER).toList(seen) }
    runCurrent()
    job.cancel()

    // The first value is the stored true, never a leading false while it loads.
    assertEquals(listOf(true), seen)
  }

  @Test
  fun sharingConsent_forwardsEveryChange() =
      runTest(UnconfinedTestDispatcher()) {
        val repository = FakeUserRepository(listOf(User(USER, "Ada", "a@b.c")))
        val seen = mutableListOf<Boolean>()
        // Unconfined, so the collector keeps up with each change rather than conflating them.
        val job = backgroundScope.launch { repository.sharingConsent(USER).toList(seen) }

        repository.setLocationSharing(USER, true, emptyList())
        repository.setLocationSharing(USER, false, emptyList())
        job.cancel()

        assertEquals(listOf(false, true, false), seen)
      }

  @Test
  fun sharingConsent_onAnObservationFailure_reportsOffThenRecovers() = runTest {
    val repository =
        FakeUserRepository(listOf(User(USER, "Ada", "a@b.c", locationSharingEnabled = true)))
    repository.observeFailure = UserRepositoryException.Unavailable()
    val seen = mutableListOf<Boolean>()
    val job = backgroundScope.launch { repository.sharingConsent(USER).toList(seen) }
    runCurrent()

    // A failed observation reports off rather than freezing on a stale true.
    assertEquals(listOf(false), seen)

    // Once it recovers, the real setting comes back after the retry delay.
    repository.observeFailure = null
    advanceTimeBy(CONSENT_RETRY_DELAY.toKotlinDuration())
    runCurrent()
    job.cancel()

    assertEquals(listOf(false, true), seen)
  }

  // ── runLiveLocationPublishing ──

  @Test
  fun run_sharesOneConsentSubscriptionAcrossEverySession() = runTest {
    val sessions = RecordingSessions()
    val coordinator = LiveLocationCoordinator(targetsOf(BAR, STAGE), sessions)
    var subscriptions = 0
    val consent = flow<Boolean> { emitAll(MutableStateFlow(true)) }.onStart { subscriptions++ }

    val job = backgroundScope.launch {
      runLiveLocationPublishing(coordinator, consent, faults::add)
    }
    runCurrent()
    job.cancel()

    // Two sessions, but the cold consent is collected once: one snapshot listener, not one per
    // team.
    assertEquals(setOf(BAR, STAGE), sessions.started.toSet())
    assertEquals(1, subscriptions)
  }

  @Test
  fun run_doesNotForwardConsentBeforeTheRealValueIsKnown() = runTest {
    val sessions = RecordingSessions()
    val coordinator = LiveLocationCoordinator(targetsOf(BAR), sessions)
    // A consent that never emits: the profile is still loading.
    val consent = flow<Boolean> { awaitCancellation() }

    val job = backgroundScope.launch {
      runLiveLocationPublishing(coordinator, consent, faults::add)
    }
    runCurrent()
    job.cancel()

    // The session is started, but sees no consent value, so it reads no GPS.
    assertEquals(listOf(BAR), sessions.started)
    assertTrue(sessions.consentSeen[BAR].isNullOrEmpty())
  }

  @Test
  fun run_forwardsTheConsentToTheSessions() = runTest {
    val sessions = RecordingSessions()
    val coordinator = LiveLocationCoordinator(targetsOf(BAR), sessions)
    val consent = MutableStateFlow(true)

    val job = backgroundScope.launch {
      runLiveLocationPublishing(coordinator, consent, faults::add)
    }
    runCurrent()
    consent.value = false
    runCurrent()
    job.cancel()

    assertEquals(listOf(true, false), sessions.consentSeen[BAR])
  }

  @Test
  fun run_reportsASessionFaultToTheHandlerWithoutFailing() = runTest {
    val sessions = RecordingSessions().apply { failing += BAR }
    val coordinator = LiveLocationCoordinator(targetsOf(BAR, STAGE), sessions)

    val job = backgroundScope.launch {
      runLiveLocationPublishing(coordinator, MutableStateFlow(true), faults::add)
    }
    runCurrent()
    job.cancel()

    // The failing session reached the handler; the other kept running.
    assertEquals(1, faults.size)
    assertTrue(sessions.active.contains(STAGE))
  }

  private val faults = mutableListOf<Throwable>()

  private fun targetsOf(vararg targets: LiveLocationTarget) = LiveLocationTargets {
    targets.toList()
  }

  /** A [LiveLocationSessions] double recording the sessions started, running, and consent seen. */
  private class RecordingSessions : LiveLocationSessions {
    val started = mutableListOf<LiveLocationTarget>()
    val active = mutableSetOf<LiveLocationTarget>()
    val consentSeen = mutableMapOf<LiveLocationTarget, MutableList<Boolean>>()
    val failing = mutableSetOf<LiveLocationTarget>()

    override suspend fun publish(target: LiveLocationTarget, sharingEnabled: Flow<Boolean>) {
      started += target
      if (target in failing) throw IllegalStateException("The session of $target failed")
      active += target
      try {
        sharingEnabled.collect { consentSeen.getOrPut(target) { mutableListOf() } += it }
        awaitCancellation()
      } finally {
        active -= target
      }
    }
  }

  private companion object {
    const val USER = "user-1"
    val BAR = LiveLocationTarget("event", "team-bar")
    val STAGE = LiveLocationTarget("event", "team-stage")
  }
}
