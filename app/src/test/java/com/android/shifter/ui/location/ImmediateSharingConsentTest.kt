// Co-authored-by: Claude Opus 4.8 <noreply@anthropic.com>
package com.swent.shifter.ui.location

import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.model.authentication.FakeAuthRepository
import com.swent.shifter.model.location.CONSENT_RETRY_DELAY
import com.swent.shifter.model.location.FakeLiveLocationRepository
import com.swent.shifter.model.location.FakeLocationProvider
import com.swent.shifter.model.location.LiveLocationPublisher
import com.swent.shifter.model.location.LocalSharingConsent
import com.swent.shifter.model.location.sharingConsent
import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.FakeMembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.team.FakeTeamRepository
import com.swent.shifter.model.team.Team
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.model.user.User
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepositoryException
import com.swent.shifter.ui.settings.LocationSharingNotice
import com.swent.shifter.ui.settings.SettingsViewModel
import com.swent.shifter.utils.MainDispatcherRule
import java.time.Instant
import kotlin.time.toKotlinDuration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Settings location-sharing switch wired to the real foreground [LiveLocationPublisher] through
 * the one [LocalSharingConsent] they share (#377), on virtual time with the location fakes.
 *
 * It is the integration the unit tests cannot be: it proves that turning sharing off in Settings
 * stops the GPS **before** Settings has read the teams or written anything, and that the publisher
 * follows the local decision through a failed save, an offline write and back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImmediateSharingConsentTest {

  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val consent = LocalSharingConsent()
  private val users =
      FakeUserRepository(listOf(User(UID, "Ada", "ada@example.com", locationSharingEnabled = true)))
  private val requests = FakeMembershipRequestRepository()
  private val teams = FakeTeamRepository()
  private val auth = FakeAuthRepository(AuthUser(UID, "ada@example.com", "Ada"))
  private val provider = FakeLocationProvider()
  private val liveLocations = FakeLiveLocationRepository(currentUserId = UID)
  private val publisher = LiveLocationPublisher(provider, liveLocations)

  /** Runs the foreground publisher on the shared consent, as the navigation root does. */
  private fun TestScope.startPublisher(): Job = backgroundScope.launch {
    publisher.publish(EVENT, TEAM, consent.applyTo(users.sharingConsent(UID), UID))
  }

  private fun settings(teamRepository: TeamRepository = teams) =
      SettingsViewModel(auth, users, requests, teamRepository, UserMode.VOLUNTEER, consent)

  /** An accepted membership and a team the volunteer is in, so the off has a position to remove. */
  private fun memberOfATeam(): Team {
    requests.add(
        EVENT,
        MembershipRequest(
            userId = UID,
            teamId = TEAM,
            availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH)),
            status = MembershipRequestStatus.ACCEPTED,
            createdAt = Instant.EPOCH,
        ),
    )
    return Team(
            id = TEAM,
            eventId = EVENT,
            name = "Bar",
            description = "",
            icon = "",
            memberIds = listOf(UID),
            createdAt = Instant.EPOCH,
        )
        .also { teams.teams += it }
  }

  @Test
  fun turningSharingOff_stopsTheGpsBeforeSettingsReadsTheTeams() =
      runTest(mainDispatcherRule.dispatcher) {
        val team = memberOfATeam()
        // The teams read is held open, so the GPS must stop without waiting for it.
        val lookupReached = CompletableDeferred<Unit>()
        val releaseLookup = CompletableDeferred<Unit>()
        val heldTeams =
            object : TeamRepository by teams {
              override suspend fun getTeamsOfMember(eventId: String, userId: String): List<Team> {
                lookupReached.complete(Unit)
                releaseLookup.await()
                return teams.getTeamsOfMember(eventId, userId)
              }
            }
        val viewModel = settings(heldTeams)
        startPublisher()
        advanceUntilIdle()
        // Sharing is on: the GPS is being read.
        assertEquals(1, provider.activeCollectors)

        viewModel.setLocationSharing(false)
        advanceUntilIdle()

        // The lookup is in progress and still held, yet the GPS has already stopped.
        assertTrue("Expected Settings to have reached the teams lookup", lookupReached.isCompleted)
        assertFalse("The lookup must still be pending", releaseLookup.isCompleted)
        assertEquals(0, provider.activeCollectors)
        // No position is left shared either, and none can be read while the GPS is off.
        assertEquals(emptyList<Any>(), liveLocations.observeTeamLocations(EVENT, TEAM).first())

        // The write then completes as before, off and with the position removed.
        releaseLookup.complete(Unit)
        advanceUntilIdle()
        assertFalse(users.users.getValue(UID).locationSharingEnabled)
        assertEquals(listOf(team), users.positionsRemovedFrom)
      }

  @Test
  fun aFailedSave_restoresConsentAndResumesTheGps() =
      runTest(mainDispatcherRule.dispatcher) {
        memberOfATeam()
        teams.failure = IllegalStateException("Network down")
        val viewModel = settings()
        startPublisher()
        advanceUntilIdle()
        assertEquals(1, provider.activeCollectors)

        viewModel.setLocationSharing(false)
        advanceUntilIdle()

        // The save failed: the switch goes back, the notice is shown, nothing was written, and the
        // publisher follows the unchanged consent again rather than the decision that never landed.
        assertEquals(true, viewModel.uiState.value.locationSharing)
        assertEquals(
            LocationSharingNotice.SAVE_FAILED,
            viewModel.uiState.value.locationSharingNotice,
        )
        assertTrue(users.users.getValue(UID).locationSharingEnabled)
        assertEquals(1, provider.activeCollectors)
      }

  @Test
  fun anOfflineOff_keepsTheGpsOffPastTheTimeout() =
      runTest(mainDispatcherRule.dispatcher) {
        // The server never confirms, as offline: the write is queued, the consent already false.
        users.unconfirmedWrite = CompletableDeferred()
        val viewModel = settings()
        startPublisher()
        advanceUntilIdle()
        assertEquals(1, provider.activeCollectors)

        viewModel.setLocationSharing(false)
        advanceTimeBy(SettingsViewModel.SAVE_TIMEOUT_MILLIS + 1)
        advanceUntilIdle()

        // Kept as saved offline, and the GPS stays off: a pending acknowledgment is not a failure.
        assertEquals(
            LocationSharingNotice.SAVED_OFFLINE,
            viewModel.uiState.value.locationSharingNotice,
        )
        assertEquals(false, viewModel.uiState.value.locationSharing)
        assertEquals(0, provider.activeCollectors)
      }

  @Test
  fun aFailClosedConsentDuringAnOff_doesNotResumeTheGps() =
      runTest(mainDispatcherRule.dispatcher) {
        // The consent stream as sharingConsent yields it across a lost observation: the persisted
        // on, then a fail-closed off, then the persisted on again once the observation recovers.
        val real = MutableStateFlow(true)
        backgroundScope.launch { publisher.publish(EVENT, TEAM, consent.applyTo(real, UID)) }
        advanceUntilIdle()
        assertEquals(1, provider.activeCollectors)

        // Settings forces off; its write is still in flight, so the real consent is still on.
        consent.turnOff(UID)
        advanceUntilIdle()
        assertEquals(0, provider.activeCollectors)

        // The observation fails then recovers: neither the fail-closed off nor the recovered on
        // ends the local decision, so the GPS does not come back on while the switch is off.
        real.value = false
        real.value = true
        advanceUntilIdle()
        assertEquals(0, provider.activeCollectors)
      }

  @Test
  fun aSuccessfulOff_releasesTheDecision_soALaterRemoteOnTakesEffect() =
      runTest(mainDispatcherRule.dispatcher) {
        // The publisher is not running when the off is saved, so it never observes the write that
        // would have ended the decision.
        val viewModel = settings()
        advanceUntilIdle()

        viewModel.setLocationSharing(false)
        advanceUntilIdle()
        assertFalse(users.users.getValue(UID).locationSharingEnabled)

        // Another device turns sharing back on, and this device syncs it.
        users.setLocationSharing(UID, true, emptyList())

        // The publisher starts again: the off decision was released by the write that confirmed it,
        // so the re-enabled consent is honoured instead of being held off by a stale local
        // decision.
        startPublisher()
        advanceUntilIdle()
        assertEquals(1, provider.activeCollectors)
      }

  @Test
  fun aDelayedConsentFalse_afterTheWrite_neverResumesTheGps() =
      runTest(mainDispatcherRule.dispatcher) {
        // The publisher's consent stream, kept separate from the write so it can lag it: the
        // Firestore snapshot has not delivered the off to the publisher yet when Settings finishes.
        val publisherConsent = MutableStateFlow(true)
        backgroundScope.launch {
          publisher.publish(EVENT, TEAM, consent.applyTo(publisherConsent, UID))
        }
        advanceUntilIdle()
        assertEquals(1, provider.activeCollectors)

        // Settings turns sharing off and its write returns, which lets it end the local decision -
        // but the publisher's stream still holds the stale true.
        val viewModel = settings()
        advanceUntilIdle()
        viewModel.setLocationSharing(false)
        advanceUntilIdle()

        // The GPS must not have resumed on that stale true during the gap.
        assertEquals(0, provider.activeCollectors)

        // The delayed off finally reaches the publisher: the GPS stays off, no flicker.
        publisherConsent.value = false
        advanceUntilIdle()
        assertEquals(0, provider.activeCollectors)

        // And a later remote on still takes effect.
        publisherConsent.value = true
        advanceUntilIdle()
        assertEquals(1, provider.activeCollectors)
      }

  @Test
  fun aFailClosedFalseAfterConfirm_thenTheCommittedOff_doesNotResumeTheGps() =
      runTest(mainDispatcherRule.dispatcher) {
        // The device's local cache, which the publisher's observation reads. The observation fails
        // once, after the off is confirmed; the fail-closed retry re-reads the cache.
        val cache = MutableStateFlow(true)
        val failObservation = CompletableDeferred<Unit>()
        var subscription = 0
        val observe = flow {
          if (subscription++ == 0) {
            emit(cache.value) // the on that was in effect
            failObservation.await() // stay open until the test fails the observation
            throw UserRepositoryException.Unavailable()
          } else {
            emitAll(cache) // the recovery reads the cache
          }
        }
        // The production composition: the fail-closed retry wraps the observation (so a failure
        // emits a synthetic off and retries), and the local decision wraps that.
        val consentStream = observe.retryWhen { cause, _ ->
          if (cause is UserRepositoryException) {
            emit(false)
            delay(CONSENT_RETRY_DELAY.toKotlinDuration())
            true
          } else {
            false
          }
        }
        backgroundScope.launch {
          publisher.publish(EVENT, TEAM, consent.applyTo(consentStream, UID))
        }
        advanceUntilIdle()
        assertEquals(1, provider.activeCollectors)

        // Settings turns sharing off; the write commits to the local cache before it confirms.
        consent.turnOff(UID)
        cache.value = false
        consent.confirmOff(UID)
        advanceUntilIdle()
        assertEquals(0, provider.activeCollectors)

        // The observation now fails: the fail-closed false ends the confirmed decision, but it is
        // not a stale on, so nothing resumes.
        failObservation.complete(Unit)
        advanceUntilIdle()
        assertEquals(0, provider.activeCollectors)

        // Past the retry delay the observation recovers and reads the committed off from the cache,
        // never a stale on, so the GPS stays off: the Firestore ordering is what rules the resume
        // out, since the off is in the cache before confirm.
        advanceTimeBy(CONSENT_RETRY_DELAY.toKotlinDuration())
        advanceUntilIdle()
        assertEquals(0, provider.activeCollectors)
      }

  private companion object {
    const val UID = "volunteer-1"
    const val EVENT = "event-1"
    const val TEAM = "team-1"
  }
}
