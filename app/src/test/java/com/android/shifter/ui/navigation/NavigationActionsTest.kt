// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navOptions
import com.swent.shifter.model.chat.ChannelKind
import com.swent.shifter.model.notification.NotificationTarget
import com.swent.shifter.model.user.UserMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NavigationActionsTest {

  private lateinit var navController: NavHostController
  private lateinit var nav: NavigationActions

  @Before
  fun setUp() {
    navController = mockk(relaxed = true)
    every { navController.currentDestination } returns null
    nav = NavigationActions(navController)
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  /** Returns the options passed to navigate(route) { ... }. */
  private fun optionsOf(route: Any): NavOptions {
    val builder = slot<NavOptionsBuilder.() -> Unit>()
    verify { navController.navigate(route, capture(builder)) }
    return navOptions(builder.captured)
  }

  @Test
  fun enterApp_clearsTheWholeBackStack() {
    val graph = mockk<NavGraph>()
    every { graph.id } returns 42
    every { navController.graph } returns graph

    nav.enterApp(Organizer)

    val options = optionsOf(Organizer)
    assertEquals(42, options.popUpToId)
    assertTrue(options.isPopUpToInclusive())
    assertTrue(options.shouldLaunchSingleTop())
  }

  @Test
  fun enterEvent_opensTheEventOnTopOfItsList() {
    nav.enterEvent(VolunteerEvent("a", "Festival"))

    val options = optionsOf(VolunteerEvent("a", "Festival"))
    assertEquals(Volunteer.Events, options.popUpToRouteObject)
    assertFalse(options.isPopUpToInclusive())
    assertTrue(options.shouldLaunchSingleTop())
  }

  @Test
  fun enterEvent_organizer_usesTheOrganizerList() {
    nav.enterEvent(OrganizerEvent("a", "Festival"))

    assertEquals(Organizer.Events, optionsOf(OrganizerEvent("a", "Festival")).popUpToRouteObject)
  }

  @Test
  fun exitEvent_returnsToTheEventsList() {
    nav.exitEvent(VolunteerEvent("a", "Festival"))

    verify { navController.popBackStack(Volunteer.Events, false) }
  }

  @Test
  fun switchTab_toAnotherTab_savesAndRestoresStacks() {
    nav.switchTab(VolunteerTabs.Map)

    val options = optionsOf(VolunteerTabs.Map)
    assertEquals(VolunteerEventScreen.Overview, options.popUpToRouteObject)
    assertTrue(options.shouldPopUpToSaveState())
    assertTrue(options.shouldRestoreState())
    assertTrue(options.shouldLaunchSingleTop())
  }

  @Test
  fun switchTab_organizer_usesTheOrganizerHomeTab() {
    nav.switchTab(OrganizerTabs.People)

    assertEquals(
        OrganizerEventScreen.Overview,
        optionsOf(OrganizerTabs.People).popUpToRouteObject,
    )
  }

  @Test
  fun switchTab_onCurrentTab_returnsToItsRoot() {
    val destination = mockk<NavDestination>(relaxed = true)
    every { destination.parent } returns null
    every { navController.currentDestination } returns destination
    mockkObject(NavDestination.Companion)
    every {
      with(NavDestination.Companion) { destination.hasRoute(VolunteerTabs.Map::class) }
    } returns true

    nav.switchTab(VolunteerTabs.Map)

    verify { navController.popBackStack(VolunteerEventScreen.Map, false) }
    verify(exactly = 0) { navController.navigate(any<Any>(), any<NavOptionsBuilder.() -> Unit>()) }
  }

  @Test
  fun navigateTo_stacksTheScreen() {
    nav.navigateTo(Volunteer.QrApply)

    assertTrue(optionsOf(Volunteer.QrApply).shouldLaunchSingleTop())
  }

  @Test
  fun openChannel_dropsTheChannelLeftOpenThenOpensTheOneAsked() {
    val channel = VolunteerEventScreen.Channel(ChannelKind.TEAM, "team", name = null)

    nav.openChannel(VolunteerTabs.Chat, channel)

    verifyOrder {
      navController.clearBackStack(VolunteerTabs.Chat::class)
      navController.navigate(VolunteerTabs.Chat, any<NavOptionsBuilder.() -> Unit>())
      navController.navigate(channel, any<NavOptionsBuilder.() -> Unit>())
    }
  }

  @Test
  fun goBack_popsWhenTheScreenIsResumed() {
    stubCurrentState(Lifecycle.State.RESUMED)

    nav.goBack()

    verify { navController.popBackStack() }
  }

  @Test
  fun goBack_isIgnoredDuringATransition() {
    stubCurrentState(Lifecycle.State.STARTED)

    nav.goBack()

    verify(exactly = 0) { navController.popBackStack() }
  }

  @Test
  fun enterEvent_clearsSavedTabStatesFromPreviousEvents() {
    nav.enterEvent(VolunteerEvent("b", "Festival"))

    VolunteerTabs.all.forEach { tab -> verify { navController.clearBackStack(tab::class) } }
  }

  @Test
  fun enterEvent_withUnavailableTabStates_stillOpensTheEvent() {
    listOf(VolunteerEvent("b", "Festival"), OrganizerEvent("b", "Festival")).forEach { event ->
      event.tabs.all.forEach { tab ->
        every { navController.clearBackStack(tab::class) } returns false
        every { navController.clearBackStack(tab) } throws
            IllegalArgumentException("Destination is not registered")
      }

      nav.enterEvent(event)

      event.tabs.all.forEach { tab -> verify { navController.clearBackStack(tab::class) } }
      assertEquals(event.list, optionsOf(event).popUpToRouteObject)
    }
  }

  @Test
  fun backTo_closesEverythingAbove() {
    nav.backTo(Volunteer.Events)

    verify { navController.popBackStack(Volunteer.Events, false) }
  }

  @Test
  fun goBack_withoutCurrentScreen_doesNothing() {
    every { navController.currentBackStackEntry } returns null

    nav.goBack()

    verify(exactly = 0) { navController.popBackStack() }
  }

  @Test
  fun switchTab_fromAnotherTab_navigates() {
    val destination = mockk<NavDestination>(relaxed = true)
    every { destination.parent } returns null
    every { navController.currentDestination } returns destination
    mockkObject(NavDestination.Companion)
    every {
      with(NavDestination.Companion) { destination.hasRoute(VolunteerTabs.Map::class) }
    } returns false

    nav.switchTab(VolunteerTabs.Map)

    verify { navController.navigate(VolunteerTabs.Map, any<NavOptionsBuilder.() -> Unit>()) }
  }

  @Test
  fun exitToSignIn_fromTheApp_entersSignedOut() {
    val graph = mockk<NavGraph>()
    every { graph.id } returns 42
    every { navController.graph } returns graph
    stubCurrentDestinationIn(SignedOut, inside = false)

    nav.exitToSignIn()

    val options = optionsOf(SignedOut)
    assertEquals(42, options.popUpToId)
    assertTrue(options.isPopUpToInclusive())
  }

  @Test
  fun exitToSignIn_whenAlreadySignedOut_doesNothing() {
    stubCurrentDestinationIn(SignedOut, inside = true)

    nav.exitToSignIn()

    verify(exactly = 0) { navController.navigate(any<Any>(), any<NavOptionsBuilder.() -> Unit>()) }
  }

  @Test
  fun exitToSignIn_withoutCurrentScreen_entersSignedOut() {
    val graph = mockk<NavGraph>()
    every { graph.id } returns 42
    every { navController.graph } returns graph

    nav.exitToSignIn()

    verify { navController.navigate(SignedOut, any<NavOptionsBuilder.() -> Unit>()) }
  }

  @Test
  fun openSettings_inOrganizerMode_opensTheOrganizerSettings() {
    stubCurrentDestinationIn(Organizer, inside = true)

    nav.openSettings()

    assertTrue(optionsOf(Organizer.ProfileSettings).shouldLaunchSingleTop())
  }

  @Test
  fun openSettings_inVolunteerMode_opensTheVolunteerSettings() {
    stubCurrentDestinationIn(Organizer, inside = false)

    nav.openSettings()

    assertTrue(optionsOf(Volunteer.ProfileSettings).shouldLaunchSingleTop())
  }

  @Test
  fun openTarget_myEvents_entersTheAppOfItsMode() {
    nav.openTarget(NotificationTarget.MyEvents(UserMode.ORGANIZER), names)

    verify { navController.navigate(Organizer, any<NavOptionsBuilder.() -> Unit>()) }
  }

  @Test
  fun openTarget_eventOverview_opensTheEventOnTopOfTheListOfItsMode() {
    nav.openTarget(
        NotificationTarget.EventOverview("e1", "Fest", UserMode.ORGANIZER, teamId = null),
        names,
    )

    verifyOrder {
      navController.navigate(Organizer, any<NavOptionsBuilder.() -> Unit>())
      navController.navigate(OrganizerEvent("e1", "Fest"), any<NavOptionsBuilder.() -> Unit>())
    }
  }

  @Test
  fun openTarget_planning_opensTheVolunteerEventOnTheDay() {
    val handle = eventState(VolunteerEvent::class)

    nav.openTarget(NotificationTarget.Planning("e1", "Fest", day = "2026-11-27"), names)

    verifyOrder {
      navController.navigate(Volunteer, any<NavOptionsBuilder.() -> Unit>())
      navController.navigate(VolunteerEvent("e1", "Fest"), any<NavOptionsBuilder.() -> Unit>())
    }
    assertEquals("2026-11-27", handle.get<String>(OPEN_DAY_KEY))
  }

  @Test
  fun openTarget_eventOverviewOfATeam_asksForTheTeam() {
    val handle = eventState(OrganizerEvent::class)

    nav.openTarget(
        NotificationTarget.EventOverview("e1", "Fest", UserMode.ORGANIZER, teamId = "bar"),
        names,
    )

    assertEquals("bar", handle.get<String>(OPEN_TEAM_KEY))
  }

  @Test
  fun openTarget_eventOverviewOfAManager_asksForTheTeamInTheVolunteerEvent() {
    val handle = eventState(VolunteerEvent::class)

    nav.openTarget(
        NotificationTarget.EventOverview("e1", "Fest", UserMode.VOLUNTEER, teamId = "bar"),
        names,
    )

    assertEquals("bar", handle.get<String>(OPEN_TEAM_KEY))
  }

  @Test
  fun openTarget_eventOverviewWithoutATeam_asksForNone() {
    val handle = eventState(OrganizerEvent::class)

    nav.openTarget(
        NotificationTarget.EventOverview("e1", "Fest", UserMode.ORGANIZER, teamId = null),
        names,
    )

    assertNull(handle.get<String>(OPEN_TEAM_KEY))
  }

  /** The saved state of the event graph [graph], where openTarget writes what to show. */
  private fun eventState(graph: kotlin.reflect.KClass<*>): SavedStateHandle {
    val handle = SavedStateHandle()
    val event = mockk<NavBackStackEntry>()
    every { event.savedStateHandle } returns handle
    every { navController.getBackStackEntry(graph) } returns event
    return handle
  }

  @Test
  fun openTarget_missionOfAManager_opensTheVolunteerEvent() {
    nav.openTarget(NotificationTarget.Mission("e1", "Fest", UserMode.VOLUNTEER, "m1"), names)

    verifyOrder {
      navController.navigate(Volunteer, any<NavOptionsBuilder.() -> Unit>())
      navController.navigate(VolunteerEvent("e1", "Fest"), any<NavOptionsBuilder.() -> Unit>())
    }
  }

  @Test
  fun openTarget_teamChannel_opensItWithoutAName() {
    nav.openTarget(
        NotificationTarget.Channel("e1", "Fest", UserMode.VOLUNTEER, ChannelKind.TEAM, "bar"),
        names,
    )

    val channel = VolunteerEventScreen.Channel(ChannelKind.TEAM, "bar", name = null)
    verifyOrder {
      navController.navigate(VolunteerEvent("e1", "Fest"), any<NavOptionsBuilder.() -> Unit>())
      navController.navigate(VolunteerTabs.Chat, any<NavOptionsBuilder.() -> Unit>())
      navController.navigate(channel, any<NavOptionsBuilder.() -> Unit>())
    }
  }

  @Test
  fun openTarget_generalChannel_opensItUnderItsName() {
    nav.openTarget(
        NotificationTarget.Channel("e1", "Fest", UserMode.ORGANIZER, ChannelKind.GENERAL, null),
        names,
    )

    val channel = OrganizerEventScreen.Channel(ChannelKind.GENERAL, null, "General")
    verifyOrder {
      navController.navigate(OrganizerEvent("e1", "Fest"), any<NavOptionsBuilder.() -> Unit>())
      navController.navigate(OrganizerTabs.Chat, any<NavOptionsBuilder.() -> Unit>())
      navController.navigate(channel, any<NavOptionsBuilder.() -> Unit>())
    }
  }

  @Test
  fun openTarget_adminChannel_opensItUnderItsName() {
    nav.openTarget(
        NotificationTarget.Channel("e1", "Fest", UserMode.ORGANIZER, ChannelKind.ADMIN, null),
        names,
    )

    val channel = OrganizerEventScreen.Channel(ChannelKind.ADMIN, null, "Admin")
    verify { navController.navigate(channel, any<NavOptionsBuilder.() -> Unit>()) }
  }

  @Test
  fun openTarget_requests_opensPeopleOnTheRequests() {
    val handle = SavedStateHandle()
    val event = mockk<NavBackStackEntry>()
    every { event.savedStateHandle } returns handle
    every { navController.getBackStackEntry(OrganizerEvent::class) } returns event

    nav.openTarget(NotificationTarget.Requests("e1", "Fest"), names)

    verifyOrder {
      navController.navigate(Organizer, any<NavOptionsBuilder.() -> Unit>())
      navController.navigate(OrganizerEvent("e1", "Fest"), any<NavOptionsBuilder.() -> Unit>())
      navController.navigate(OrganizerTabs.People, any<NavOptionsBuilder.() -> Unit>())
    }
    assertEquals(true, handle.get<Boolean>(OPEN_REQUESTS_KEY))
  }

  @Test
  fun isInEitherApp_inTheOrganizerApp_isTrue() {
    stubCurrentDestinationIn(Volunteer to false, Organizer to true)

    assertTrue(nav.isInEitherApp())
  }

  @Test
  fun isInEitherApp_whileSigningIn_isFalse() {
    stubCurrentDestinationIn(Volunteer to false, Organizer to false)

    assertFalse(nav.isInEitherApp())
  }

  /** The names of the event's own channels, as the app's strings give them. */
  private val names = ChannelNames(general = "General", admin = "Admin")

  /** Puts the current screen inside each of [routes] whose flag is true, and outside the others. */
  private fun stubCurrentDestinationIn(vararg routes: Pair<Any, Boolean>) {
    val destination = mockk<NavDestination>(relaxed = true)
    every { destination.parent } returns null
    every { navController.currentDestination } returns destination
    mockkObject(NavDestination.Companion)
    for ((route, inside) in routes) {
      every { with(NavDestination.Companion) { destination.hasRoute(route::class) } } returns inside
    }
  }

  private fun stubCurrentDestinationIn(route: Any, inside: Boolean) {
    val destination = mockk<NavDestination>(relaxed = true)
    every { destination.parent } returns null
    every { navController.currentDestination } returns destination
    mockkObject(NavDestination.Companion)
    every { with(NavDestination.Companion) { destination.hasRoute(route::class) } } returns inside
  }

  private fun stubCurrentState(state: Lifecycle.State) {
    val entry = mockk<NavBackStackEntry>()
    every { entry.lifecycle.currentState } returns state
    every { navController.currentBackStackEntry } returns entry
  }
}
