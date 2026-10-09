// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.membership

import com.swent.shifter.model.membership.AvailabilitySlot
import com.swent.shifter.model.membership.MembershipRequest
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryException
import com.swent.shifter.model.membership.MembershipRequestStatus
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
  private val requests = listOf(request("alice"), request("bob"))

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    coEvery { repository.getMembershipRequestsByEId("event") } returns requests
    coEvery { repository.accept("event", any()) } returns Unit
    coEvery { repository.reject("event", any()) } returns Unit
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun initiallyLoadsAndPreventsOverlappingLoads() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository)
        assertTrue(vm.uiState.value.isLoading)
        vm.loadRequests()
        advanceUntilIdle()
        assertEquals(requests, vm.uiState.value.requests)
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 1) { repository.getMembershipRequestsByEId("event") }
      }

  @Test
  fun loadFailureKeepsDataAndCanBeClearedAndRetried() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository)
        advanceUntilIdle()
        coEvery { repository.getMembershipRequestsByEId("event") } throws
            MembershipRequestRepositoryException.Unavailable()
        vm.loadRequests()
        advanceUntilIdle()
        assertEquals(requests, vm.uiState.value.requests)
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
  fun acceptsAndRejectsWithoutRemovingRequests() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository)
        advanceUntilIdle()
        vm.accept("alice")
        assertEquals(setOf("alice"), vm.uiState.value.processingRequestIds)
        assertEquals(requests, vm.uiState.value.requests)
        advanceUntilIdle()
        assertEquals(
            requests[0].copy(status = MembershipRequestStatus.ACCEPTED),
            vm.uiState.value.requests[0],
        )
        vm.reject("alice")
        advanceUntilIdle()
        assertEquals(
            listOf(requests[0].copy(status = MembershipRequestStatus.REJECTED), requests[1]),
            vm.uiState.value.requests,
        )
        assertTrue(vm.uiState.value.processingRequestIds.isEmpty())
        coVerify(exactly = 1) { repository.accept("event", "alice") }
        coVerify(exactly = 1) { repository.reject("event", "alice") }
      }

  @Test
  fun simultaneousDecisionsPreserveBothResultsAndBlockDoubleClicksAndReload() =
      runTest(dispatcher) {
        val alice = CompletableDeferred<Unit>()
        val bob = CompletableDeferred<Unit>()
        coEvery { repository.accept("event", "alice") } coAnswers { alice.await() }
        coEvery { repository.reject("event", "bob") } coAnswers { bob.await() }
        val vm = MembershipRequestVM("event", repository)
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
        alice.complete(Unit)
        advanceUntilIdle()
        assertEquals(
            listOf(MembershipRequestStatus.ACCEPTED, MembershipRequestStatus.REJECTED),
            vm.uiState.value.requests.map { it.status },
        )
        assertTrue(vm.uiState.value.processingRequestIds.isEmpty())
        coVerify(exactly = 1) { repository.accept("event", "alice") }
        coVerify(exactly = 0) { repository.reject("event", "alice") }
        coVerify(exactly = 1) { repository.getMembershipRequestsByEId("event") }
      }

  @Test
  fun decisionsDuringReloadAndUnknownUsersAreIgnored() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository)
        advanceUntilIdle()
        vm.accept("unknown")
        vm.loadRequests()
        vm.accept("alice")
        vm.reject("bob")
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.accept(any(), any()) }
        coVerify(exactly = 0) { repository.reject(any(), any()) }
      }

  @Test
  fun decisionErrorsKeepStatusReleaseButtonsAndAllowRetry() =
      runTest(dispatcher) {
        val vm = MembershipRequestVM("event", repository)
        advanceUntilIdle()
        coEvery { repository.accept("event", "alice") } throws
            MembershipRequestRepositoryException.PermissionDenied()
        vm.accept("alice")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.errorMsg!!.contains("permission"))
        assertEquals(requests, vm.uiState.value.requests)
        assertTrue(vm.uiState.value.processingRequestIds.isEmpty())
        coEvery { repository.reject("event", "alice") } throws
            MembershipRequestRepositoryException.Unknown()
        vm.reject("alice")
        advanceUntilIdle()
        assertEquals("Something went wrong. Please try again.", vm.uiState.value.errorMsg)
        assertEquals(requests, vm.uiState.value.requests)
        assertTrue(vm.uiState.value.processingRequestIds.isEmpty())
        coEvery { repository.accept("event", "alice") } returns Unit
        vm.accept("alice")
        advanceUntilIdle()
        assertNull(vm.uiState.value.errorMsg)
        assertEquals(MembershipRequestStatus.ACCEPTED, vm.uiState.value.requests[0].status)
      }

  @Test
  fun cancellationDoesNotBecomeAnErrorAndReleasesBusyState() =
      runTest(dispatcher) {
        coEvery { repository.getMembershipRequestsByEId("event") } throws CancellationException()
        val vm = MembershipRequestVM("event", repository)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.errorMsg)
        coEvery { repository.getMembershipRequestsByEId("event") } returns requests
        vm.loadRequests()
        advanceUntilIdle()
        coEvery { repository.accept("event", "alice") } throws CancellationException()
        vm.accept("alice")
        advanceUntilIdle()
        assertEquals(requests, vm.uiState.value.requests)
        assertTrue(vm.uiState.value.processingRequestIds.isEmpty())
        assertNull(vm.uiState.value.errorMsg)
      }

  private fun request(uid: String) =
      MembershipRequest(
          id = uid,
          userId = uid,
          availability = listOf(AvailabilitySlot(Instant.EPOCH, Instant.EPOCH.plusSeconds(3600))),
          createdAt = Instant.EPOCH,
      )
}
