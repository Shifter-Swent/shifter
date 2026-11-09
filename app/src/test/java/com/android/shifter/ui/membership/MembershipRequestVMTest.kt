// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.membership

import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.user.PublicProfile
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.UserRepositoryException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MembershipRequestVMTest {
  private val dispatcher = StandardTestDispatcher()
  private val repository = mockk<MembershipRequestRepository>()
  private val userRepository = mockk<UserRepository>()
  private val requests = listOf(request("alice"), request("bob"))

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    coEvery { userRepository.getPublicProfile(any()) } answers
        {
          val uid = firstArg<String>()
          PublicProfile(uid = uid, displayName = "$uid Smith")
        }
    coEvery { repository.getMembershipRequestsByEId("event") } returns requests
    coEvery { repository.accept("event", any()) } returns Unit
    coEvery { repository.reject("event", any()) } returns Unit
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun loadRequests_initiallyLoadsAndPreventsOverlappingLoads() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository, userRepository)
        assertTrue(vm.uiState.value.isLoading)
        vm.loadRequests()
        advanceUntilIdle()
        assertEquals(items(requests), vm.uiState.value.requests)
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 1) { repository.getMembershipRequestsByEId("event") }
        coVerify(exactly = 1) { userRepository.getPublicProfile("alice") }
        coVerify(exactly = 1) { userRepository.getPublicProfile("bob") }
      }

  @Test
  fun loadRequests_onFailure_keepsDataAndCanBeClearedAndRetried() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        coEvery { repository.getMembershipRequestsByEId("event") } throws
            MembershipRequestRepositoryException.Unavailable()
        vm.loadRequests()
        advanceUntilIdle()
        assertEquals(items(requests), vm.uiState.value.requests)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.errorMsg!!.contains("unavailable"))
        vm.clearError()
        assertNull(vm.uiState.value.errorMsg)
        coEvery { repository.getMembershipRequestsByEId("event") } returns emptyList()
        vm.loadRequests()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.requests.isEmpty())
        assertNull(vm.uiState.value.errorMsg)
      }

  @Test
  fun acceptAndReject_doNotRemoveRequests() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        vm.accept("alice")
        assertEquals(setOf("alice"), vm.uiState.value.processingRequestIds)
        assertEquals(items(requests), vm.uiState.value.requests)
        advanceUntilIdle()
        assertEquals(
            items(listOf(requests[0].copy(status = MembershipRequestStatus.ACCEPTED))).single(),
            vm.uiState.value.requests[0],
        )
        vm.reject("alice")
        advanceUntilIdle()
        assertEquals(
            items(listOf(requests[0].copy(status = MembershipRequestStatus.REJECTED), requests[1])),
            vm.uiState.value.requests,
        )
        assertTrue(vm.uiState.value.processingRequestIds.isEmpty())
        coVerify(exactly = 1) { repository.accept("event", "alice") }
        coVerify(exactly = 1) { repository.reject("event", "alice") }
      }

  @Test
  fun simultaneousDecisions_preserveBothResultsAndBlockDoubleClicksAndDelayTheReload() =
      runTest(dispatcher) {
        val alice = CompletableDeferred<Unit>()
        val bob = CompletableDeferred<Unit>()
        coEvery { repository.accept("event", "alice") } coAnswers { alice.await() }
        coEvery { repository.reject("event", "bob") } coAnswers { bob.await() }
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        vm.accept("alice")
        vm.accept("alice")
        vm.reject("alice")
        vm.reject("bob")
        vm.loadRequests()
        runCurrent()
        assertEquals(setOf("alice", "bob"), vm.uiState.value.processingRequestIds)
        bob.complete(Unit)
        runCurrent()
        assertEquals(setOf("alice"), vm.uiState.value.processingRequestIds)
        coVerify(exactly = 1) { repository.getMembershipRequestsByEId("event") }
        val decided =
            listOf(
                requests[0].copy(status = MembershipRequestStatus.ACCEPTED),
                requests[1].copy(status = MembershipRequestStatus.REJECTED),
            )
        coEvery { repository.getMembershipRequestsByEId("event") } returns decided
        alice.complete(Unit)
        advanceUntilIdle()
        assertEquals(items(decided), vm.uiState.value.requests)
        assertTrue(vm.uiState.value.processingRequestIds.isEmpty())
        coVerify(exactly = 1) { repository.accept("event", "alice") }
        coVerify(exactly = 0) { repository.reject("event", "alice") }
        coVerify(exactly = 2) { repository.getMembershipRequestsByEId("event") }
      }

  @Test
  fun decisions_forUnknownUsers_areIgnored() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        vm.accept("unknown")
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.accept(any(), any()) }
      }

  @Test
  fun decision_duringAReload_isAppliedThenReloadsOnceWritten() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        val stale = CompletableDeferred<List<MembershipRequest>>()
        coEvery { repository.getMembershipRequestsByEId("event") } coAnswers { stale.await() }
        vm.loadRequests()
        runCurrent()

        val written = CompletableDeferred<Unit>()
        coEvery { repository.accept("event", "alice") } coAnswers { written.await() }
        vm.accept("alice")
        stale.complete(requests)
        runCurrent()
        assertEquals(MembershipRequestStatus.PENDING, vm.uiState.value.requests[0].request.status)

        // A request received meanwhile shows once the decision is written.
        val updated =
            listOf(requests[0].copy(status = MembershipRequestStatus.ACCEPTED), requests[1]) +
                request("carol")
        coEvery { repository.getMembershipRequestsByEId("event") } returns updated
        written.complete(Unit)
        advanceUntilIdle()

        assertEquals(items(updated), vm.uiState.value.requests)
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 3) { repository.getMembershipRequestsByEId("event") }
      }

  @Test
  fun decisions_duringAReload_reloadOnceAfterTheLastOneEvenIfItDidNotCancelIt() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        coEvery { repository.getMembershipRequestsByEId("event") } coAnswers
            {
              CompletableDeferred<List<MembershipRequest>>().await()
            }
        vm.loadRequests()
        runCurrent()
        val alice = CompletableDeferred<Unit>()
        val bob = CompletableDeferred<Unit>()
        coEvery { repository.accept("event", "alice") } coAnswers { alice.await() }
        coEvery { repository.accept("event", "bob") } coAnswers { bob.await() }
        // Alice's decision cancels the reload, Bob's finds none to cancel and finishes last.
        vm.accept("alice")
        vm.accept("bob")
        runCurrent()
        coEvery { repository.getMembershipRequestsByEId("event") } returns requests
        alice.complete(Unit)
        runCurrent()
        coVerify(exactly = 2) { repository.getMembershipRequestsByEId("event") }

        bob.complete(Unit)
        advanceUntilIdle()
        coVerify(exactly = 3) { repository.getMembershipRequestsByEId("event") }
      }

  @Test
  fun failedDecision_keepsItsErrorAndLeavesTheReloadForTheNextCall() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        val stale = CompletableDeferred<List<MembershipRequest>>()
        coEvery { repository.getMembershipRequestsByEId("event") } coAnswers { stale.await() }
        vm.loadRequests()
        runCurrent()
        coEvery { repository.accept("event", "alice") } throws
            MembershipRequestRepositoryException.Unavailable()
        vm.accept("alice")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.errorMsg!!.contains("unavailable"))
        coVerify(exactly = 2) { repository.getMembershipRequestsByEId("event") }

        coEvery { repository.accept("event", "alice") } returns Unit
        coEvery { repository.getMembershipRequestsByEId("event") } returns requests
        vm.accept("alice")
        advanceUntilIdle()
        assertNull(vm.uiState.value.errorMsg)
        coVerify(exactly = 3) { repository.getMembershipRequestsByEId("event") }
      }

  @Test
  fun loadRequests_afterASuccess_keepsTheListWithoutSpinnerThenShowsTheChanges() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        val answer = CompletableDeferred<List<MembershipRequest>>()
        coEvery { repository.getMembershipRequestsByEId("event") } coAnswers { answer.await() }

        vm.loadRequests()
        runCurrent()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(items(requests), vm.uiState.value.requests)

        val updated = requests + request("carol")
        answer.complete(updated)
        advanceUntilIdle()
        assertEquals(items(updated), vm.uiState.value.requests)
      }

  @Test
  fun loadRequests_afterAnEmptyList_doesNotShowTheSpinnerAgain() =
      runTest(dispatcher) {
        coEvery { repository.getMembershipRequestsByEId("event") } returns emptyList()
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()

        vm.loadRequests()
        assertFalse(vm.uiState.value.isLoading)
        advanceUntilIdle()
      }

  @Test
  fun loadRequests_afterAFirstFailure_showsTheSpinnerOnRetry() =
      runTest(dispatcher) {
        coEvery { repository.getMembershipRequestsByEId("event") } throws
            MembershipRequestRepositoryException.Unavailable()
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        coEvery { repository.getMembershipRequestsByEId("event") } returns requests

        vm.loadRequests()
        assertTrue(vm.uiState.value.isLoading)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(items(requests), vm.uiState.value.requests)
      }

  @Test
  fun decisions_onError_keepStatusReleaseButtonsAndAllowRetry() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        coEvery { repository.accept("event", "alice") } throws
            MembershipRequestRepositoryException.PermissionDenied()
        vm.accept("alice")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.errorMsg!!.contains("permission"))
        assertEquals(items(requests), vm.uiState.value.requests)
        assertTrue(vm.uiState.value.processingRequestIds.isEmpty())
        coEvery { repository.reject("event", "alice") } throws
            MembershipRequestRepositoryException.Unknown()
        vm.reject("alice")
        advanceUntilIdle()
        assertEquals("Something went wrong. Please try again.", vm.uiState.value.errorMsg)
        assertEquals(items(requests), vm.uiState.value.requests)
        assertTrue(vm.uiState.value.processingRequestIds.isEmpty())
        coEvery { repository.accept("event", "alice") } returns Unit
        vm.accept("alice")
        advanceUntilIdle()
        assertNull(vm.uiState.value.errorMsg)
        assertEquals(MembershipRequestStatus.ACCEPTED, vm.uiState.value.requests[0].request.status)
      }

  @Test
  fun cancellation_doesNotBecomeAnErrorAndReleasesBusyState() =
      runTest(dispatcher) {
        coEvery { repository.getMembershipRequestsByEId("event") } throws CancellationException()
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMsg)
        coEvery { repository.getMembershipRequestsByEId("event") } returns requests
        vm.loadRequests()
        advanceUntilIdle()
        coEvery { repository.accept("event", "alice") } throws CancellationException()
        vm.accept("alice")
        advanceUntilIdle()
        assertEquals(items(requests), vm.uiState.value.requests)
        assertTrue(vm.uiState.value.processingRequestIds.isEmpty())
        assertNull(vm.uiState.value.errorMsg)
      }

  @Test
  fun loadRequests_withMissingOrBlankProfiles_usesFallbackNames() =
      runTest(dispatcher) {
        coEvery { userRepository.getPublicProfile("alice") } returns null
        coEvery { userRepository.getPublicProfile("bob") } returns PublicProfile("bob", "  ")
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        assertEquals(requests, vm.uiState.value.requests.map { it.request })
        assertEquals(
            listOf("Unknown volunteer", "Unknown volunteer"),
            vm.uiState.value.requests.map { it.displayName },
        )
        assertNull(vm.uiState.value.errorMsg)
        assertFalse(vm.uiState.value.isLoading)
      }

  @Test
  fun loadRequests_onProfileError_keepsRequestsAndOtherNamesAndAllowsRetry() =
      runTest(dispatcher) {
        coEvery { userRepository.getPublicProfile("alice") } throws
            UserRepositoryException.PermissionDenied()
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        assertEquals(requests, vm.uiState.value.requests.map { it.request })
        assertEquals(
            listOf("Unknown volunteer", "bob Smith"),
            vm.uiState.value.requests.map { it.displayName },
        )
        assertNotNull(vm.uiState.value.errorMsg)
        assertFalse(vm.uiState.value.isLoading)
        vm.accept("alice")
        advanceUntilIdle()
        assertEquals(MembershipRequestStatus.ACCEPTED, vm.uiState.value.requests[0].request.status)
        coEvery { userRepository.getPublicProfile("alice") } returns
            PublicProfile("alice", "Alice Smith")
        vm.loadRequests()
        advanceUntilIdle()
        assertEquals("Alice Smith", vm.uiState.value.requests[0].displayName)
        assertNull(vm.uiState.value.errorMsg)
      }

  @Test
  fun loadRequests_onProfileCancellation_keepsPreviousDataAndReleasesLoading() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository, userRepository)
        advanceUntilIdle()
        coEvery { userRepository.getPublicProfile("bob") } throws CancellationException()
        vm.loadRequests()
        advanceUntilIdle()
        assertEquals(items(requests), vm.uiState.value.requests)
        assertNull(vm.uiState.value.errorMsg)
        assertFalse(vm.uiState.value.isLoading)
      }

  private fun items(requests: List<MembershipRequest>) = requests.map {
    MembershipRequestItemUIState(it, "${it.userId} Smith")
  }

  private fun request(uid: String) =
      MembershipRequest(
          id = uid,
          userId = uid,
          teamId = "bar",
          availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
          createdAt = Instant.EPOCH,
      )
}
