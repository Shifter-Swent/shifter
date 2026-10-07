// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import kotlinx.serialization.Serializable

/*
 * SignedOut
 *   └── SignIn
 * Volunteer
 *   ├── Events (VM-00), ProfileSettings, QrApply
 *   └── VolunteerEvent(eventId): Overview (VM-01), Map, Discussions
 * Organizer
 *   ├── Events (OR-00), ProfileSettings
 *   └── OrganizerEvent(eventId): Overview, People, Map, Discussions
 *
 * Links between types are getters, not vals, to avoid initialization cycles.
 */

sealed interface App {
  val start: Any
}

sealed interface Destination {
  val title: String
  val tab: Tab?
    get() = null
}

sealed interface Tab {
  val home: Destination
  val label: String
  val icon: ImageVector
  val testTag: String
  val set: TabSet
}

/** The tabs of one Event mode, in display order. */
sealed interface TabSet {
  val all: List<Tab>
  val home: Tab
}

/**
 * One event opened in Event mode. Screens inside read [eventId] from this graph's back stack entry.
 */
sealed interface EventMode {
  val eventId: String
  val tabs: TabSet
  /** The events list this event is opened from. */
  val list: Destination
}

// ── Signed out ──────────────────────────────────────────────────────────────

@Serializable
data object SignedOut : App {
  override val start
    get() = SignIn

  @Serializable
  data object SignIn : Destination {
    override val title = "Sign in"
  }
}

// ── Volunteer ───────────────────────────────────────────────────────────────

@Serializable
data object Volunteer : App {
  override val start
    get() = Events

  @Serializable
  data object Events : Destination {
    override val title = "My events"
  }

  @Serializable
  data object ProfileSettings : Destination {
    override val title = "Profile & Settings"
  }

  @Serializable
  data object QrApply : Destination {
    override val title = "Join an event"
  }
}

@Serializable
data class VolunteerEvent(override val eventId: String) : EventMode {
  override val tabs
    get() = VolunteerTabs

  override val list
    get() = Volunteer.Events
}

object VolunteerTabs : TabSet {
  override val all
    get() = listOf(Overview, Map, Discussions)

  override val home
    get() = Overview

  @Serializable
  data object Overview : Tab {
    override val home
      get() = VolunteerEventScreen.Overview

    override val label = "Overview"
    override val icon = Icons.Filled.Home
    override val testTag = NavigationTestTags.OVERVIEW_TAB
    override val set
      get() = VolunteerTabs
  }

  @Serializable
  data object Map : Tab {
    override val home
      get() = VolunteerEventScreen.Map

    override val label = "Map"
    override val icon = Icons.Filled.Map
    override val testTag = NavigationTestTags.MAP_TAB
    override val set
      get() = VolunteerTabs
  }

  @Serializable
  data object Discussions : Tab {
    override val home
      get() = VolunteerEventScreen.Discussions

    override val label = "Discussions"
    override val icon = Icons.AutoMirrored.Filled.Chat
    override val testTag = NavigationTestTags.DISCUSSIONS_TAB
    override val set
      get() = VolunteerTabs
  }
}

sealed interface VolunteerEventScreen : Destination {
  @Serializable
  data object Overview : VolunteerEventScreen {
    override val title = "Overview"
    override val tab
      get() = VolunteerTabs.Overview
  }

  @Serializable
  data object Map : VolunteerEventScreen {
    override val title = "Map"
    override val tab
      get() = VolunteerTabs.Map
  }

  @Serializable
  data object Discussions : VolunteerEventScreen {
    override val title = "Discussions"
    override val tab
      get() = VolunteerTabs.Discussions
  }
}

// ── Organizer ───────────────────────────────────────────────────────────────

@Serializable
data object Organizer : App {
  override val start
    get() = Events

  @Serializable
  data object Events : Destination {
    override val title = "My organized events"
  }

  @Serializable
  data object ProfileSettings : Destination {
    override val title = "Profile & Settings"
  }
}

@Serializable
data class OrganizerEvent(override val eventId: String) : EventMode {
  override val tabs
    get() = OrganizerTabs

  override val list
    get() = Organizer.Events
}

object OrganizerTabs : TabSet {
  override val all
    get() = listOf(Overview, People, Map, Discussions)

  override val home
    get() = Overview

  @Serializable
  data object Overview : Tab {
    override val home
      get() = OrganizerEventScreen.Overview

    override val label = "Overview"
    override val icon = Icons.Filled.Home
    override val testTag = NavigationTestTags.OVERVIEW_TAB
    override val set
      get() = OrganizerTabs
  }

  @Serializable
  data object People : Tab {
    override val home
      get() = OrganizerEventScreen.People

    override val label = "People"
    override val icon = Icons.Filled.People
    override val testTag = NavigationTestTags.PEOPLE_TAB
    override val set
      get() = OrganizerTabs
  }

  @Serializable
  data object Map : Tab {
    override val home
      get() = OrganizerEventScreen.Map

    override val label = "Map"
    override val icon = Icons.Filled.Map
    override val testTag = NavigationTestTags.MAP_TAB
    override val set
      get() = OrganizerTabs
  }

  @Serializable
  data object Discussions : Tab {
    override val home
      get() = OrganizerEventScreen.Discussions

    override val label = "Discussions"
    override val icon = Icons.AutoMirrored.Filled.Chat
    override val testTag = NavigationTestTags.DISCUSSIONS_TAB
    override val set
      get() = OrganizerTabs
  }
}

sealed interface OrganizerEventScreen : Destination {
  @Serializable
  data object Overview : OrganizerEventScreen {
    override val title = "Overview"
    override val tab
      get() = OrganizerTabs.Overview
  }

  @Serializable
  data object People : OrganizerEventScreen {
    override val title = "People"
    override val tab
      get() = OrganizerTabs.People
  }

  @Serializable
  data object Map : OrganizerEventScreen {
    override val title = "Map"
    override val tab
      get() = OrganizerTabs.Map
  }

  @Serializable
  data object Discussions : OrganizerEventScreen {
    override val title = "Discussions"
    override val tab
      get() = OrganizerTabs.Discussions
  }
}

/** Opens Organizer mode if the user was there last time, Volunteer mode otherwise. */
fun startApp(isSignedIn: Boolean, wasOrganizer: Boolean): App =
    when {
      !isSignedIn -> SignedOut
      wasOrganizer -> Organizer
      else -> Volunteer
    }

// ── Navigation actions ──────────────────────────────────────────────────────

@Composable
fun rememberNavigationActions(navController: NavHostController): NavigationActions =
    remember(navController) { NavigationActions(navController) }

open class NavigationActions(private val navController: NavHostController) {

  /** Clears the whole back stack: sign in, sign out, or switch between Volunteer and Organizer. */
  open fun enterApp(app: App) {
    navController.navigate(app) {
      popUpTo(navController.graph.id) { inclusive = true }
      launchSingleTop = true
    }
  }

  /** Opens an event on top of its events list, replacing any event already open. */
  open fun enterEvent(event: EventMode) {
    event.tabs.all.forEach { navController.clearBackStack(it) }
    navController.navigate(event) {
      popUpTo(event.list)
      launchSingleTop = true
    }
  }

  open fun exitEvent(event: EventMode) {
    navController.popBackStack(event.list, inclusive = false)
  }

  /** Each tab keeps its own stack. Tapping the current tab goes back to its root. */
  open fun switchTab(tab: Tab) {
    if (isOnTab(tab)) {
      navController.popBackStack(tab.home, inclusive = false)
      return
    }
    navController.navigate(tab) {
      popUpTo(tab.set.home.home) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  open fun navigateTo(destination: Destination) {
    navController.navigate(destination) { launchSingleTop = true }
  }

  /** Ignored during a transition, so a double tap doesn't pop two screens. */
  open fun goBack() {
    if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
      navController.popBackStack()
    }
  }

  open fun backTo(destination: Destination) {
    navController.popBackStack(destination, inclusive = false)
  }

  private fun isOnTab(tab: Tab): Boolean =
      navController.currentDestination?.hierarchy?.any { it.hasRoute(tab::class) } == true
}
