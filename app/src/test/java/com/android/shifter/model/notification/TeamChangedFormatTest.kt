// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.R
import com.swent.shifter.model.user.UserMode
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers, through [TeamChangedFormat.format], what a manager reads for a join and for a departure,
 * with and without missions, what is shown for a document the app cannot read, and where a tap
 * goes.
 */
class TeamChangedFormatTest {

  /** Zurich, where the texts are written, in June, so a mission at 10:00 UTC reads "12:00". */
  private val texts = FakeNotificationTexts(ZoneId.of("Europe/Zurich"))

  private val format = TeamChangedFormat()

  private val serveAtTheBar =
      MissionLine(
          "m1",
          "Serve at the bar",
          Instant.parse("2026-06-21T10:00:00Z"),
          Instant.parse("2026-06-21T15:00:00Z"),
      )

  private val nightService =
      MissionLine(
          "m2",
          "Bar · night service",
          Instant.parse("2026-06-21T18:00:00Z"),
          Instant.parse("2026-06-21T22:00:00Z"),
      )

  private fun teamChanged(
      id: String = "notification-1",
      change: String = NotificationData.Value.JOINED,
      userName: String? = "Alice Martin",
      teamName: String? = "Bar",
      teamId: String? = "team-bar",
      missions: List<MissionLine>? = null,
      createdAt: Instant = Instant.parse("2026-06-21T10:10:00Z"),
  ) =
      inboxNotification(
          id = id,
          kind = NotificationKind.TEAM_CHANGED,
          eventId = "event-1",
          eventTitle = "Summer Festival",
          createdAt = createdAt,
          data =
              buildMap {
                put(NotificationData.Key.CHANGE, change)
                userName?.let { put(NotificationData.Key.USER_NAME, it) }
                teamName?.let { put(NotificationData.Key.TEAM_NAME, it) }
                teamId?.let { put(NotificationData.Key.TEAM_ID, it) }
                missions?.let { put(NotificationData.Key.MISSIONS, MissionLine.encodeAll(it)) }
              },
      )

  @Test
  fun format_withAJoin_showsWhoJoinedWhichTeam() {
    val shown = format.format(listOf(teamChanged()), texts)

    assertEquals(
        listOf(
            AppNotification(
                kind = NotificationKind.TEAM_CHANGED,
                key = "notification-1",
                title = "Summer Festival",
                text = "${R.string.notification_team_changed_joined}[Alice Martin, Bar]",
                at = Instant.parse("2026-06-21T10:10:00Z"),
                target =
                    NotificationTarget.EventOverview(
                        "event-1",
                        "Summer Festival",
                        UserMode.VOLUNTEER,
                        "team-bar",
                    ),
            )
        ),
        shown,
    )
  }

  @Test
  fun format_withADepartureFromMissions_listsThemWithTheirStart() {
    val item =
        teamChanged(
            change = NotificationData.Value.LEFT,
            userName = "Léa Petit",
            missions = listOf(serveAtTheBar, nightService),
        )

    val separator = "${R.string.notification_team_changed_mission_separator}[]"
    val mission = R.string.notification_team_changed_mission
    assertEquals(
        "${R.string.notification_team_changed_left_on_missions}[Léa Petit, Bar, " +
            "$mission[Serve at the bar, 12:00]$separator$mission[Bar · night service, 20:00]]",
        format.format(listOf(item), texts).single().text,
    )
  }

  @Test
  fun format_withADepartureFromNoMission_onlySaysTheyLeft() {
    val item = teamChanged(change = NotificationData.Value.LEFT, userName = "Léa Petit")

    assertEquals(
        "${R.string.notification_team_changed_left}[Léa Petit, Bar]",
        format.format(listOf(item), texts).single().text,
    )
  }

  @Test
  fun format_withADepartureFromOnlyMalformedMissions_onlySaysTheyLeft() {
    val item =
        inboxNotification(
            kind = NotificationKind.TEAM_CHANGED,
            data =
                mapOf(
                    NotificationData.Key.CHANGE to NotificationData.Value.LEFT,
                    NotificationData.Key.USER_NAME to "Léa Petit",
                    NotificationData.Key.TEAM_NAME to "Bar",
                    NotificationData.Key.MISSIONS to "broken\nm1|Bar|tomorrow|2026-06-21T15:00:00Z",
                ),
        )

    assertEquals(
        "${R.string.notification_team_changed_left}[Léa Petit, Bar]",
        format.format(listOf(item), texts).single().text,
    )
  }

  @Test
  fun format_withoutATeam_opensTheOverviewOnNoCard() {
    val target = format.format(listOf(teamChanged(teamId = null)), texts).single().target

    assertEquals(
        NotificationTarget.EventOverview("event-1", "Summer Festival", UserMode.VOLUNTEER, null),
        target,
    )
  }

  @Test
  fun format_withoutAName_standsOneInForIt() {
    val someone = "${R.string.notification_team_changed_someone}[]"

    assertEquals(
        "${R.string.notification_team_changed_joined}[$someone, Bar]",
        format.format(listOf(teamChanged(userName = null)), texts).single().text,
    )
    assertEquals(
        "${R.string.notification_team_changed_left}[$someone, Bar]",
        format
            .format(
                listOf(teamChanged(change = NotificationData.Value.LEFT, userName = " ")),
                texts,
            )
            .single()
            .text,
    )
  }

  @Test
  fun format_withoutATeamNameOrAKnownChange_showsNothing() {
    val unreadable =
        listOf(
            teamChanged(teamName = null),
            teamChanged(teamName = " "),
            teamChanged(change = "SOMETHING_ELSE"),
        )

    assertEquals(emptyList<AppNotification>(), format.format(unreadable, texts))
  }

  @Test
  fun format_withSeveralChangesOfOneTeam_showsEachOfThem() {
    val items =
        listOf(
            teamChanged(id = "notification-1", change = NotificationData.Value.LEFT),
            teamChanged(id = "notification-2"),
        )

    assertEquals(
        listOf("notification-1", "notification-2"),
        format.format(items, texts).map { it.key },
    )
  }

  @Test
  fun format_withoutAnyItem_showsNothing() {
    assertEquals(emptyList<AppNotification>(), format.format(emptyList(), texts))
  }
}
