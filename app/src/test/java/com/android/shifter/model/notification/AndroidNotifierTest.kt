// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.swent.shifter.model.user.UserMode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidNotifierTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val manager = context.getSystemService(NotificationManager::class.java)
  private lateinit var notifier: AndroidNotifier

  @Before
  fun setUp() {
    notifier = AndroidNotifier(context)
  }

  @Test
  fun build_createsOneChannelPerKind_namedAsItsSwitchInSettings() {
    NotificationKind.entries.forEach { kind ->
      val channel = manager.getNotificationChannel(CHANNEL_IDS.getValue(kind))
      assertEquals(context.getString(kind.channelName), channel.name)
      assertEquals(
          context.getString(kind.channelDescription, *kind.channelDescriptionArgs),
          channel.description,
      )
    }
  }

  @Test
  fun build_keepsEachChannelsId_soAUsersSettingsSurviveARename() {
    assertEquals(CHANNEL_IDS.values.toSet(), manager.notificationChannels.map { it.id }.toSet())
  }

  @Test
  fun build_letsOnlyAMissedCheckInPopUp() {
    NotificationKind.entries.forEach { kind ->
      val expected =
          if (kind == NotificationKind.MISSED_CHECK_IN) NotificationManager.IMPORTANCE_HIGH
          else NotificationManager.IMPORTANCE_DEFAULT
      assertEquals(kind.name, expected, channel(kind).importance)
    }
  }

  @Test
  fun show_postsEachUnderItsIdentity_onItsKindsChannel() {
    notifier.show(listOf(notification(NotificationKind.OPEN_SPOTS, "m1", text = "Needs 2")))

    val posted = posted("OPEN_SPOTS:m1")
    assertEquals("notification_open_spots", posted.channelId)
    assertEquals("Fest", posted.extras.getString(Notification.EXTRA_TITLE))
    assertEquals("Needs 2", posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    assertEquals(AT.toEpochMilli(), posted.`when`)
  }

  @Test
  fun show_theSameIdentityAgain_replacesTheNotification() {
    notifier.show(listOf(notification(NotificationKind.NEW_APPLICATION, "e1", text = "1 new")))
    notifier.show(listOf(notification(NotificationKind.NEW_APPLICATION, "e1", text = "2 new")))

    assertEquals(setOf("NEW_APPLICATION:e1"), notifier.activeIdentities())
    val posted = posted("NEW_APPLICATION:e1")
    assertEquals("2 new", posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
  }

  @Test
  fun show_twoKindsWithTheSameKey_showsBoth() {
    notifier.show(
        listOf(
            notification(NotificationKind.MISSION_CHANGED, "m1"),
            notification(NotificationKind.OPEN_SPOTS, "m1"),
        )
    )

    assertEquals(setOf("MISSION_CHANGED:m1", "OPEN_SPOTS:m1"), notifier.activeIdentities())
  }

  @Test
  fun show_groupsAnEventsNotifications_underASilentSummaryWithItsTitle() {
    notifier.show(
        listOf(
            notification(NotificationKind.OPEN_SPOTS, "m1"),
            notification(NotificationKind.MISSION_CHANGED, "m2", text = "Moved"),
        )
    )

    val summary = posted("e1")
    assertTrue(summary.flags and Notification.FLAG_GROUP_SUMMARY != 0)
    assertEquals("e1", summary.group)
    assertEquals("Fest", summary.extras.getString(Notification.EXTRA_TITLE))
    assertEquals(Notification.GROUP_ALERT_CHILDREN, summary.groupAlertBehavior)
    assertEquals("e1", posted("OPEN_SPOTS:m1").group)
  }

  @Test
  fun show_aGroupAlreadySummarized_keepsItsSummaryAsItIs() {
    notifier.show(listOf(notification(NotificationKind.OPEN_SPOTS, "m1")))
    notifier.show(
        listOf(notification(NotificationKind.OPEN_SPOTS, "m2", target = mission("m2", "Fest 2026")))
    )

    val summary = posted("e1")
    assertEquals("Fest", summary.extras.getString(Notification.EXTRA_TITLE))
  }

  @Test
  fun show_theSummary_isOnTheChannelOfTheLatestNotificationOnAChannelThatIsOn() {
    turnOff(NotificationKind.OPEN_SPOTS)

    notifier.show(
        listOf(
            notification(NotificationKind.MISSION_CHANGED, "m1"),
            notification(NotificationKind.OPEN_SPOTS, "m2"),
        )
    )

    assertEquals("notification_mission_changed", posted("e1").channelId)
  }

  @Test
  fun show_onlyOnChannelsTurnedOff_postsNoSummary() {
    turnOff(NotificationKind.OPEN_SPOTS)

    notifier.show(listOf(notification(NotificationKind.OPEN_SPOTS, "m1")))

    assertNull(postedOrNull("e1"))
  }

  @Test
  fun show_removesASummaryWhoseNotificationsWereAllDismissed_onceItsGroupIsNoLongerPostedInto() {
    notifier.show(listOf(notification(NotificationKind.OPEN_SPOTS, "m1")))
    dismiss("OPEN_SPOTS:m1")

    notifier.show(listOf(myEvents("e2")))
    assertNotNull(postedOrNull("e1"))

    notifier.show(listOf(myEvents("e3")))
    assertNull(postedOrNull("e1"))
  }

  @Test
  fun show_intoAGroupLeftAlone_keepsItsSummaryAsItIs() {
    notifier.show(listOf(notification(NotificationKind.OPEN_SPOTS, "m1")))
    notifier.show(listOf(myEvents("e2")))
    dismiss("OPEN_SPOTS:m1")
    val summary = posted("e1")

    notifier.show(listOf(notification(NotificationKind.OPEN_SPOTS, "m2")))

    assertSame(summary, posted("e1"))
    assertEquals("e1", posted("OPEN_SPOTS:m2").group)
  }

  @Test
  fun show_nothing_changesNothing() {
    notifier.show(listOf(notification(NotificationKind.OPEN_SPOTS, "m1")))
    dismiss("OPEN_SPOTS:m1")

    notifier.show(emptyList())

    assertEquals(1, shadowOf(manager).allNotifications.size)
  }

  @Test
  fun show_myEvents_hasNoGroupNorSummary() {
    notifier.show(
        listOf(
            notification(
                NotificationKind.APPLICATION_DECIDED,
                "e1",
                target = NotificationTarget.MyEvents(UserMode.VOLUNTEER),
            )
        )
    )

    assertNull(posted("APPLICATION_DECIDED:e1").group)
    assertEquals(1, shadowOf(manager).allNotifications.size)
  }

  @Test
  fun show_myEventsWithAnEventsNotifications_postsOnlyTheEventsSummary() {
    notifier.show(
        listOf(
            notification(NotificationKind.OPEN_SPOTS, "m1"),
            notification(
                NotificationKind.APPLICATION_DECIDED,
                "e2",
                target = NotificationTarget.MyEvents(UserMode.VOLUNTEER),
            ),
        )
    )

    val summaries =
        shadowOf(manager).allNotifications.filter {
          it.flags and Notification.FLAG_GROUP_SUMMARY != 0
        }
    assertEquals(listOf("e1"), summaries.map { it.group })
  }

  @Test
  fun activeIdentities_leavesOutTheSummaries_andWhatTheUserDismissed() {
    notifier.show(
        listOf(
            notification(NotificationKind.OPEN_SPOTS, "m1"),
            notification(NotificationKind.OPEN_SPOTS, "m2"),
        )
    )

    dismiss("OPEN_SPOTS:m1")

    assertEquals(setOf("OPEN_SPOTS:m2"), notifier.activeIdentities())
  }

  @Test
  fun show_aTap_opensEachNotificationsOwnTarget() {
    val first = NotificationTarget.Mission("e1", "Fest", UserMode.ORGANIZER, "m1")
    val second = NotificationTarget.Requests("e1", "Fest")
    notifier.show(
        listOf(
            notification(NotificationKind.OPEN_SPOTS, "m1", target = first),
            notification(NotificationKind.NEW_APPLICATION, "e1", target = second),
        )
    )

    assertEquals(first, tappedTarget("OPEN_SPOTS:m1"))
    assertEquals(second, tappedTarget("NEW_APPLICATION:e1"))
  }

  @Test
  fun show_replacedUnderItsIdentity_tapsOpenTheNewTarget() {
    val old = NotificationTarget.Requests("e1", "Fest")
    val new = NotificationTarget.Requests("e1", "Fest 2026")
    notifier.show(listOf(notification(NotificationKind.NEW_APPLICATION, "e1", target = old)))
    notifier.show(listOf(notification(NotificationKind.NEW_APPLICATION, "e1", target = new)))

    assertEquals(new, tappedTarget("NEW_APPLICATION:e1"))
  }

  @Test
  fun canShow_isFalse_withTheNotificationsTurnedOff() {
    assertTrue(notifier.canShow())

    shadowOf(manager).setNotificationsEnabled(false)

    assertFalse(notifier.canShow())
  }

  @Test
  fun canShow_withOneChannelTurnedOff_staysTrue() {
    turnOff(NotificationKind.OPEN_SPOTS)

    assertTrue(notifier.canShow())
  }

  @Test
  fun clear_removesEveryNotification_summariesIncluded() {
    notifier.show(listOf(notification(NotificationKind.OPEN_SPOTS, "m1"), myEvents("e2")))

    notifier.clear()

    assertEquals(emptyList<Notification>(), manager.activeNotifications.map { it.notification })
  }

  /** Turns the channel of [kind] off, as the user does in the phone's settings. */
  private fun turnOff(kind: NotificationKind) {
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL_IDS.getValue(kind), "Off", NotificationManager.IMPORTANCE_NONE)
    )
  }

  private fun channel(kind: NotificationKind) =
      manager.getNotificationChannel(CHANNEL_IDS.getValue(kind))

  private fun postedOrNull(tag: String): Notification? =
      manager.activeNotifications.singleOrNull { it.tag == tag }?.notification

  private fun posted(tag: String): Notification = checkNotNull(postedOrNull(tag)) { tag }

  /** Dismisses the notification tagged [tag], as the user does with a swipe. */
  private fun dismiss(tag: String) {
    val shown = manager.activeNotifications.single { it.tag == tag }
    manager.cancel(shown.tag, shown.id)
  }

  private fun myEvents(key: String) =
      notification(
          NotificationKind.APPLICATION_DECIDED,
          key,
          target = NotificationTarget.MyEvents(UserMode.VOLUNTEER),
      )

  private fun mission(key: String, eventTitle: String = "Fest") =
      NotificationTarget.Mission("e1", eventTitle, UserMode.ORGANIZER, key)

  private fun tappedTarget(identity: String): NotificationTarget? {
    val tap = posted(identity).contentIntent
    return shadowOf(tap).savedIntent.notificationTarget()
  }

  private fun notification(
      kind: NotificationKind,
      key: String,
      text: String = "Text",
      target: NotificationTarget =
          NotificationTarget.Mission("e1", "Fest", UserMode.ORGANIZER, key),
  ) = AppNotification(kind, key, title = "Fest", text = text, at = AT, target = target)

  private companion object {
    val AT: Instant = Instant.parse("2026-11-27T08:00:00Z")

    /** Written out, so renaming a kind cannot move its channel without a test failing. */
    val CHANNEL_IDS =
        mapOf(
            NotificationKind.NEW_APPLICATION to "notification_new_application",
            NotificationKind.APPLICATION_DECIDED to "notification_application_decided",
            NotificationKind.TEAM_CHANGED to "notification_team_changed",
            NotificationKind.MISSION_ASSIGNED to "notification_mission_assigned",
            NotificationKind.MISSION_CHANGED to "notification_mission_changed",
            NotificationKind.ANNOUNCEMENT to "notification_announcement",
            NotificationKind.MISSED_CHECK_IN to "notification_missed_check_in",
            NotificationKind.OPEN_SPOTS to "notification_open_spots",
        )
  }
}
