// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.navigation

import com.swent.shifter.model.chat.ChannelKind
import com.swent.shifter.model.mission.Mission
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationRoutesTest {

  private val tabSets = listOf(VolunteerTabs, OrganizerTabs)

  private val screensOutsideEventMode =
      listOf(
          SignedOut.SignIn,
          Starting.Loading,
          ChoosingMode.Choice,
          Volunteer.Events,
          Volunteer.ProfileSettings,
          Volunteer.QrApply,
          Volunteer.Scan,
          Organizer.Events,
          Organizer.ProfileSettings,
          Organizer.CreateEvent,
          Organizer.EventCreated("e3"),
      )

  @Test
  fun startApp_picksTheRightApp() {
    assertEquals(SignedOut, startApp(isSignedIn = false))
    // The mode saved on the profile is read before entering Volunteer or Organizer.
    assertEquals(Starting, startApp(isSignedIn = true))
  }

  @Test
  fun apps_startOnTheirFirstScreen() {
    assertEquals(SignedOut.SignIn, SignedOut.start)
    assertEquals(Starting.Loading, Starting.start)
    assertEquals(ChoosingMode.Choice, ChoosingMode.start)
    assertEquals(Volunteer.Events, Volunteer.start)
    assertEquals(Organizer.Events, Organizer.start)
  }

  @Test
  fun screensOutsideEventMode_haveATitleAndNoTab() {
    screensOutsideEventMode.forEach {
      assertTrue(it.title.isNotBlank())
      assertNull(it.tab)
    }
  }

  @Test
  fun tabSets_listTheirTabsInOrder() {
    assertEquals(
        listOf(VolunteerTabs.Overview, VolunteerTabs.Map, VolunteerTabs.Chat),
        VolunteerTabs.all,
    )
    assertEquals(
        listOf(
            OrganizerTabs.Overview,
            OrganizerTabs.People,
            OrganizerTabs.Map,
            OrganizerTabs.Chat,
            OrganizerTabs.Sharing,
        ),
        OrganizerTabs.all,
    )
    assertEquals(VolunteerTabs.Overview, VolunteerTabs.home)
    assertEquals(OrganizerTabs.Overview, OrganizerTabs.home)
  }

  @Test
  fun everyTab_isConsistentWithItsScreen() {
    tabSets.forEach { set ->
      set.all.forEach { tab ->
        assertSame(set, tab.set)
        assertEquals(tab, tab.home.tab)
        assertTrue(tab.home.title.isNotBlank())
        assertTrue(tab.label.isNotBlank())
        assertTrue(tab.testTag.isNotBlank())
        assertNotNull(tab.icon)
      }
    }
  }

  @Test
  fun tabTestTags_areUniqueWithinASet() {
    tabSets.forEach { set -> assertEquals(set.all.size, set.all.map { it.testTag }.toSet().size) }
  }

  @Test
  fun events_knowTheirTabsAndList() {
    val volunteerEvent = VolunteerEvent("e1", "Festival")
    assertEquals("e1", volunteerEvent.eventId)
    assertEquals("Festival", volunteerEvent.eventTitle)
    assertSame(VolunteerTabs, volunteerEvent.tabs)
    assertEquals(Volunteer.Events, volunteerEvent.list)

    val organizerEvent = OrganizerEvent("e2", "Festival")
    assertEquals("e2", organizerEvent.eventId)
    assertSame(OrganizerTabs, organizerEvent.tabs)
    assertEquals(Organizer.Events, organizerEvent.list)
  }

  @Test
  fun contactChannel_withATeam_isItsChannelWithoutName() {
    assertEquals(
        VolunteerEventScreen.Channel(ChannelKind.TEAM, "team", name = null),
        contactChannel(mission(teamId = "team"), "General"),
    )
  }

  @Test
  fun contactChannel_withoutTeam_isTheGeneralChannel() {
    assertEquals(
        VolunteerEventScreen.Channel(ChannelKind.GENERAL, null, "General"),
        contactChannel(mission(teamId = null), "General"),
    )
  }

  private fun mission(teamId: String?) =
      Mission(
          eventId = "event",
          title = "Pick up the litter",
          description = "",
          teamId = teamId,
          volunteersNeeded = 1,
          startAt = Instant.EPOCH,
          endAt = Instant.EPOCH,
          createdAt = Instant.EPOCH,
      )
}
