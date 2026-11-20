// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.model.chat.ChannelKind
import com.swent.shifter.model.user.UserMode
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTargetTest {

  @Test
  fun everyTarget_isReadBackAsWritten() {
    val targets =
        listOf(
            NotificationTarget.EventOverview("e1", "Fest", UserMode.VOLUNTEER, teamId = "bar"),
            NotificationTarget.Planning("e1", "Fest", day = "2026-11-27"),
            NotificationTarget.Mission("e1", "Fest", UserMode.ORGANIZER, missionId = "m1"),
            NotificationTarget.Channel("e1", "Fest", UserMode.VOLUNTEER, ChannelKind.TEAM, "bar"),
            NotificationTarget.Channel("e1", "Fest", UserMode.ORGANIZER, ChannelKind.GENERAL, null),
            NotificationTarget.EventOverview("e1", "Fest", UserMode.ORGANIZER, teamId = null),
            NotificationTarget.Requests("e1", "Fest"),
            NotificationTarget.MyEvents(UserMode.ORGANIZER),
        )

    for (target in targets) {
      val written = Json.encodeToString(NotificationTarget.serializer(), target)
      assertEquals(target, Json.decodeFromString(NotificationTarget.serializer(), written))
    }
  }

  @Test(expected = DateTimeParseException::class)
  fun planning_onSomethingElseThanADate_isRefused() {
    NotificationTarget.Planning("e1", "Fest", day = "tomorrow")
  }

  @Test(expected = IllegalArgumentException::class)
  fun teamChannel_withoutATeam_isRefused() {
    NotificationTarget.Channel("e1", "Fest", UserMode.VOLUNTEER, ChannelKind.TEAM, teamId = null)
  }

  @Test(expected = IllegalArgumentException::class)
  fun eventChannel_withATeam_isRefused() {
    NotificationTarget.Channel("e1", "Fest", UserMode.ORGANIZER, ChannelKind.GENERAL, "bar")
  }

  @Test(expected = DateTimeParseException::class)
  fun planning_readBackFromAnIntentWithABadDay_isRefused() {
    Json.decodeFromString(
        NotificationTarget.serializer(),
        """{"type":"${NotificationTarget.Planning.serializer().descriptor.serialName}",""" +
            """"eventId":"e1","eventTitle":"Fest","day":"soon"}""",
    )
  }
}
