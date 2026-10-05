package com.swent.shifter.ui.navigation

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
          Volunteer.Events,
          Volunteer.ProfileSettings,
          Volunteer.QrApply,
          Organizer.Events,
          Organizer.ProfileSettings,
      )

  @Test
  fun startApp_picksTheRightApp() {
    assertEquals(SignedOut, startApp(isSignedIn = false, wasOrganizer = false))
    assertEquals(SignedOut, startApp(isSignedIn = false, wasOrganizer = true))
    assertEquals(Volunteer, startApp(isSignedIn = true, wasOrganizer = false))
    assertEquals(Organizer, startApp(isSignedIn = true, wasOrganizer = true))
  }

  @Test
  fun apps_startOnTheirFirstScreen() {
    assertEquals(SignedOut.SignIn, SignedOut.start)
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
        listOf(VolunteerTabs.Overview, VolunteerTabs.Map, VolunteerTabs.Discussions),
        VolunteerTabs.all,
    )
    assertEquals(
        listOf(
            OrganizerTabs.Overview,
            OrganizerTabs.People,
            OrganizerTabs.Map,
            OrganizerTabs.Discussions,
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
    val volunteerEvent = VolunteerEvent("e1")
    assertEquals("e1", volunteerEvent.eventId)
    assertSame(VolunteerTabs, volunteerEvent.tabs)
    assertEquals(Volunteer.Events, volunteerEvent.list)

    val organizerEvent = OrganizerEvent("e2")
    assertEquals("e2", organizerEvent.eventId)
    assertSame(OrganizerTabs, organizerEvent.tabs)
    assertEquals(Organizer.Events, organizerEvent.list)
  }
}
