// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.navOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    nav.enterEvent(VolunteerEvent("a"))

    val options = optionsOf(VolunteerEvent("a"))
    assertEquals(Volunteer.Events, options.popUpToRouteObject)
    assertFalse(options.isPopUpToInclusive())
    assertTrue(options.shouldLaunchSingleTop())
  }

  @Test
  fun enterEvent_organizer_usesTheOrganizerList() {
    nav.enterEvent(OrganizerEvent("a"))

    assertEquals(Organizer.Events, optionsOf(OrganizerEvent("a")).popUpToRouteObject)
  }

  @Test
  fun exitEvent_returnsToTheEventsList() {
    nav.exitEvent(VolunteerEvent("a"))

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
    nav.enterEvent(VolunteerEvent("b"))

    VolunteerTabs.all.forEach { tab -> verify { navController.clearBackStack(tab) } }
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

  private fun stubCurrentState(state: Lifecycle.State) {
    val entry = mockk<NavBackStackEntry>()
    every { entry.lifecycle.currentState } returns state
    every { navController.currentBackStackEntry } returns entry
  }
}
