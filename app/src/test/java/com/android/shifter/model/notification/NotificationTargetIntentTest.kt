// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.swent.shifter.MainActivity
import com.swent.shifter.model.chat.ChannelKind
import com.swent.shifter.model.user.UserMode
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationTargetIntentTest {

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun notificationTapIntent_carriesEveryTargetToMainActivity() {
    val targets =
        listOf(
            NotificationTarget.EventOverview("e1", "Fest", UserMode.VOLUNTEER, teamId = "bar"),
            NotificationTarget.Planning("e1", "Fest", day = "2026-11-27"),
            NotificationTarget.Mission("e1", "Fest", UserMode.ORGANIZER, missionId = "m1"),
            NotificationTarget.Channel("e1", "Fest", UserMode.VOLUNTEER, ChannelKind.TEAM, "bar"),
            NotificationTarget.Requests("e1", "Fest"),
            NotificationTarget.MyEvents(UserMode.ORGANIZER),
        )

    for (target in targets) {
      val intent = notificationTapIntent(context, notification(target))

      assertEquals(MainActivity::class.java.name, intent.component?.className)
      assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
      assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
      assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
      assertEquals(target, intent.notificationTarget())
    }
  }

  @Test
  fun notificationTapIntent_ofTwoNotifications_areTwoIntentsForAndroid() {
    val target = NotificationTarget.MyEvents(UserMode.VOLUNTEER)
    val first = notificationTapIntent(context, notification(target, key = "e1"))
    val second = notificationTapIntent(context, notification(target, key = "e2"))

    // Android tells intents apart by their data, not their extras: two PendingIntents with the same
    // request code stay two.
    assertFalse(first.filterEquals(second))
    assertEquals("NEW_APPLICATION:e1", first.data?.lastPathSegment)
  }

  @Test
  fun notificationTapIntent_ofTheSameNotificationAgain_isTheSameIntentForAndroid() {
    val first =
        notificationTapIntent(
            context,
            notification(NotificationTarget.MyEvents(UserMode.VOLUNTEER)),
        )
    // Replaced under the same identity, e.g. "3 applications" after "2", with a newer target.
    val again =
        notificationTapIntent(context, notification(NotificationTarget.Requests("e1", "Fest")))

    // So FLAG_UPDATE_CURRENT updates the target of the PendingIntent already shown.
    assertTrue(first.filterEquals(again))
    assertEquals(NotificationTarget.Requests("e1", "Fest"), again.notificationTarget())
  }

  @Test
  fun notificationTarget_withoutOne_isNull() {
    assertNull(Intent(context, MainActivity::class.java).notificationTarget())
  }

  @Test
  fun notificationTarget_thatIsNotJson_isNull() {
    val intent = Intent().putExtra(NOTIFICATION_TARGET_EXTRA, "not a target")

    assertNull(intent.notificationTarget())
  }

  @Test
  fun notificationTarget_ofAnUnknownKind_isNull() {
    val intent = Intent().putExtra(NOTIFICATION_TARGET_EXTRA, """{"type":"Unknown"}""")

    assertNull(intent.notificationTarget())
  }

  @Test
  fun notificationTarget_ofAPlanningWhoseDayIsNotADate_isNull() {
    val planning = NotificationTarget.Planning("e1", "Fest", day = "2026-11-27")
    val written = written(planning)
    val broken = Intent().putExtra(NOTIFICATION_TARGET_EXTRA, written!!.replace("2026-11-27", "x"))

    assertNull(broken.notificationTarget())
  }

  @Test
  fun notificationTarget_ofATeamChannelWithoutItsTeam_isNull() {
    val channel =
        NotificationTarget.Channel("e1", "Fest", UserMode.VOLUNTEER, ChannelKind.TEAM, "bar")
    val written = written(channel)
    val broken = Intent().putExtra(NOTIFICATION_TARGET_EXTRA, written!!.replace("\"bar\"", "null"))

    assertNull(broken.notificationTarget())
  }

  @Test
  fun notificationTargetToOpen_onAFreshStart_isTheTapped() {
    val target = NotificationTarget.Requests("e1", "Fest")

    assertEquals(target, notificationTargetToOpen(tap(target), savedInstanceState = null))
  }

  @Test
  fun notificationTargetToOpen_fromTheAppIcon_isNull() {
    assertNull(notificationTargetToOpen(Intent(context, MainActivity::class.java), null))
  }

  @Test
  fun notificationTargetToOpen_reopenedFromRecents_isNull() {
    // Android 9 to 11 create the activity again with the intent of the tap, already opened.
    val reopened =
        tap(NotificationTarget.Requests("e1", "Fest"))
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)

    assertNull(notificationTargetToOpen(reopened, savedInstanceState = null))
  }

  @Test
  fun notificationTargetToOpen_recreatedBeforeItOpened_isTheSavedOne() {
    // Tapped signed out, then rotated on Sign-in: the intent is not read again, the state is.
    val waiting = NotificationTarget.MyEvents(UserMode.ORGANIZER)
    val saved = Bundle().apply { putNotificationTargetToOpen(waiting) }

    assertEquals(waiting, notificationTargetToOpen(tap(waiting), saved))
  }

  @Test
  fun notificationTargetToOpen_recreatedOnceItOpened_isNull() {
    // Nothing left to open is saved, and the intent's target is not opened twice.
    val saved = Bundle().apply { putNotificationTargetToOpen(null) }

    assertNull(notificationTargetToOpen(tap(NotificationTarget.Requests("e1", "Fest")), saved))
  }

  /** The intent of a tap on a notification opening [target]. */
  private fun tap(target: NotificationTarget) = notificationTapIntent(context, notification(target))

  /** The JSON [notificationTapIntent] writes for [target]. */
  private fun written(target: NotificationTarget): String? =
      notificationTapIntent(context, notification(target)).getStringExtra(NOTIFICATION_TARGET_EXTRA)

  /** A notification of new applications with [key], opening [target]. */
  private fun notification(target: NotificationTarget, key: String = "e1") =
      AppNotification(
          kind = NotificationKind.NEW_APPLICATION,
          key = key,
          title = "New applications",
          text = "Ada applied",
          at = Instant.EPOCH,
          target = target,
      )
}
