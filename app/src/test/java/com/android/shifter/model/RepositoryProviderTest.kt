// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model

import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventLocation
import com.swent.shifter.model.event.EventType
import com.swent.shifter.model.event.FakeEventRepository
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.mission.FakeMissionRepository
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.user.FakeUserRepository
import com.swent.shifter.ui.mission.AddMissionViewModel
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * Covers the container itself, not a backend: a plain JUnit test with no Robolectric and no
 * Firebase, which is the point of a provider that only knows interfaces.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepositoryProviderTest {

  private val dispatcher = StandardTestDispatcher()
  private val zone = ZoneId.of("Europe/Zurich")
  private val clock = Clock.fixed(Instant.parse("2027-06-01T10:00:00Z"), zone)
  private val eventDay = LocalDate.of(2027, 6, 20)

  private val auth = mockk<AuthRepository>()
  private val events = FakeEventRepository()
  private val users = FakeUserRepository()
  private val memberships = mockk<MembershipRequestRepository>()
  private val missions = FakeMissionRepository()
  private val teams = mockk<TeamRepository>()

  private val provider =
      RepositoryProvider(
          authRepository = auth,
          eventRepository = events,
          userRepository = users,
          membershipRequestRepository = memberships,
          missionRepository = missions,
          teamRepository = teams,
      )

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun providerExposesEveryRepositoryItWasBuiltWith() {
    assertSame(auth, provider.authRepository)
    assertSame(events, provider.eventRepository)
    assertSame(users, provider.userRepository)
    assertSame(memberships, provider.membershipRequestRepository)
    assertSame(missions, provider.missionRepository)
    assertSame(teams, provider.teamRepository)
  }

  /**
   * What a composition root does: take out the two repositories the ViewModel declares and inject
   * them. The mission landing in the container's own fake is what proves the ViewModel was given
   * the live instances, and that it needs nothing but the interfaces to run.
   */
  @Test
  fun aViewModelBuiltFromTheProviderWorksOnTheInstancesItHolds() = runTest {
    val event = events.createEvent(sampleEvent())

    val viewModel =
        AddMissionViewModel(
            eventId = event.id,
            missionRepository = provider.missionRepository,
            eventRepository = provider.eventRepository,
            clock = clock,
        )
    advanceUntilIdle()

    // The event the ViewModel shows comes out of the fake held by the container.
    assertEquals(event.title, viewModel.uiState.value.event?.title)

    viewModel.onTitleChange("Welcome desk")
    viewModel.onDescriptionChange("Greet the visitors at the entrance")
    viewModel.onDayChange(eventDay)
    viewModel.onStartTimeChange(LocalTime.of(10, 0))
    viewModel.onEndTimeChange(LocalTime.of(12, 0))
    viewModel.addMission()
    advanceUntilIdle()

    assertEquals(listOf("Welcome desk"), missions.created.map { it.title })
  }

  private fun sampleEvent() =
      Event(
          organizerId = "organizer-1",
          title = "Local Food Drive",
          description = "Collect food for the local food bank",
          type = EventType.FOOD,
          startAt = eventDay.atTime(10, 0).atZone(zone).toInstant(),
          endAt = eventDay.atTime(18, 0).atZone(zone).toInstant(),
          location = EventLocation(address = "Grand Place, Lille"),
          createdAt = clock.instant(),
      )
}
