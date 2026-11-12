// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import com.swent.shifter.model.chat.ChannelKind
import com.swent.shifter.model.chat.ChatChannelId
import kotlinx.serialization.Serializable

/*
 * SignedOut
 *   └── SignIn → OrganizerSignIn
 * Starting: reads the mode saved on the profile, then enters Volunteer or Organizer
 * Volunteer
 *   ├── Events (VM-00), ProfileSettings, QrApply
 *   └── VolunteerEvent(eventId, eventTitle): Overview (VM-01) (+ AddMission, EditMission),
 *       Map, Discussions (+ Channel)
 * Organizer
 *   ├── Events (OR-00), ProfileSettings, CreateEvent → TeamSetup(eventId) → EventCreated(eventId)
 *   └── OrganizerEvent(eventId, eventTitle): Overview (+ AddMission, ManageTeams), People, Map,
 *       Discussions (+ Channel)
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
 * A chat channel opened on top of the Discussions tab of either mode: the channel of the team
 * [teamId] for [ChannelKind.TEAM], or one of the event's for the other kinds, with no [teamId]. Its
 * [name] is in the route, so the bar shows it without loading the team.
 */
sealed interface ChannelScreen : Destination {
  val kind: ChannelKind
  val teamId: String?
  val name: String
  override val title: String
    get() = name

  /** The channel this screen shows, in the event [eventId]. */
  fun channelId(eventId: String): ChatChannelId =
      when (kind) {
        ChannelKind.TEAM -> ChatChannelId.Team(eventId, checkNotNull(teamId))
        ChannelKind.GENERAL -> ChatChannelId.General(eventId)
        ChannelKind.ADMIN -> ChatChannelId.Admin(eventId)
      }
}

/**
 * One event opened in Event mode. Screens inside read [eventId] from this graph's back stack entry.
 */
sealed interface EventMode {
  val eventId: String
  /** The event's name, shown in the top bar of its tabs; passed when the event is opened. */
  val eventTitle: String
  val tabs: TabSet
  /** The events list this event is opened from. */
  val list: Destination
}

// ── Signed out ──────────────────────────────────────────────────────────────

@Serializable
data object SignedOut : App {
  override val start
    get() = SignIn

  /** The Volunteer sign-in, the one the app opens on. */
  @Serializable
  data object SignIn : Destination {
    override val title = "Sign in"
  }

  /** The Organizer sign-in, opened on top of the Volunteer one. */
  @Serializable
  data object OrganizerSignIn : Destination {
    override val title = "Organizer sign in"
  }
}

// ── Starting ────────────────────────────────────────────────────────────────

/** Opened at launch with a session, while the mode saved on the profile is read. */
@Serializable
data object Starting : App {
  override val start
    get() = Loading

  @Serializable
  data object Loading : Destination {
    override val title = "Loading"
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
data class VolunteerEvent(override val eventId: String, override val eventTitle: String) :
    EventMode {
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
    override val icon = Icons.Outlined.Dashboard
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

  /** Mission creation form of a manager, opened on top of the event's Overview. */
  @Serializable
  data object AddMission : VolunteerEventScreen {
    override val title = "Add mission"
    override val tab
      get() = VolunteerTabs.Overview
  }

  /** The same form editing [missionId], a mission of a team the manager leads. */
  @Serializable
  data class EditMission(val missionId: String) : VolunteerEventScreen {
    override val title
      get() = "Edit mission"

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

  /** A chat channel, opened from Discussions. */
  @Serializable
  data class Channel(
      override val kind: ChannelKind,
      override val teamId: String?,
      override val name: String,
  ) : VolunteerEventScreen, ChannelScreen {
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

  @Serializable
  data object CreateEvent : Destination {
    override val title = "Create event"
  }

  /**
   * Teams of the event [eventId] just created, in place of the creation form: the event is already
   * saved, so Back from here returns to the events list rather than to a form that would save it
   * again.
   */
  @Serializable
  data class TeamSetup(val eventId: String) : Destination {
    override val title
      get() = "Team setup"
  }

  /** Shows the join code of the event [eventId] just created, in place of the creation form. */
  @Serializable
  data class EventCreated(val eventId: String) : Destination {
    override val title
      get() = "Event created"
  }
}

@Serializable
data class OrganizerEvent(override val eventId: String, override val eventTitle: String) :
    EventMode {
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
    override val icon = Icons.Outlined.Dashboard
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

  /** Mission creation form, opened on top of the event's Overview. */
  @Serializable
  data object AddMission : OrganizerEventScreen {
    override val title = "Add mission"
    override val tab
      get() = OrganizerTabs.Overview
  }

  /** Edit form of the mission [missionId], opened on top of the event's Overview. */
  @Serializable
  data class EditMission(val missionId: String) : OrganizerEventScreen {
    override val title
      get() = "Edit mission"

    override val tab
      get() = OrganizerTabs.Overview
  }

  /** Teams of an event that already exists, opened on top of its Overview. */
  @Serializable
  data object ManageTeams : OrganizerEventScreen {
    override val title = "Manage teams"
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

  /** A chat channel, opened from Discussions. */
  @Serializable
  data class Channel(
      override val kind: ChannelKind,
      override val teamId: String?,
      override val name: String,
  ) : OrganizerEventScreen, ChannelScreen {
    override val tab
      get() = OrganizerTabs.Discussions
  }
}

/**
 * Opens sign-in without a session. With one, [Starting] reads the mode saved on the profile before
 * entering it.
 */
fun startApp(isSignedIn: Boolean): App = if (isSignedIn) Starting else SignedOut

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

  /**
   * Returns to sign-in when the session ends, unless already signed out: Settings navigates there
   * itself after signing out, and entering it twice would recreate the sign-in screen.
   */
  open fun exitToSignIn() {
    if (!isInApp(SignedOut)) enterApp(SignedOut)
  }

  /** Opens an event on top of its events list, replacing any event already open. */
  open fun enterEvent(event: EventMode) {
    // Clear by route class so tabs absent from the current graph are harmless.
    event.tabs.all.forEach { navController.clearBackStack(it::class) }
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

  /** Opens the Settings of the mode the current screen belongs to, so as not to leave that mode. */
  open fun openSettings() {
    navigateTo(if (isInApp(Organizer)) Organizer.ProfileSettings else Volunteer.ProfileSettings)
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

  /** Whether the current screen belongs to [app]. */
  private fun isInApp(app: App): Boolean =
      navController.currentDestination?.hierarchy?.any { it.hasRoute(app::class) } == true

  private fun isOnTab(tab: Tab): Boolean =
      navController.currentDestination?.hierarchy?.any { it.hasRoute(tab::class) } == true
}
