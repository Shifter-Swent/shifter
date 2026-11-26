// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import com.swent.shifter.R
import com.swent.shifter.model.checkin.CheckIn
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.event.Event
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.membership.MembershipRequestStatus
import com.swent.shifter.model.mission.Mission
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.mission.isCompleted
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.time.formatTime
import com.swent.shifter.model.user.UserMode
import com.swent.shifter.model.user.UserRepository
import com.swent.shifter.model.user.displayNames
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * How long after its start a volunteer still not checked in to their mission is told. Also written
 * in the text of its switch in Settings (see [channelDescriptionArgs]), so the text follows it.
 */
internal val MISSED_CHECK_IN_DELAY: Duration = Duration.ofMinutes(10)

/** When a volunteer of this mission not checked in yet is told to its manager. */
private val Mission.missedCheckInAt: Instant
  get() = startAt + MISSED_CHECK_IN_DELAY

/**
 * The assignees of this mission with no check-in among [checkIns]. A check-in still being sent
 * counts as one, as on the manager's now card (`AttendanceMission.missingAssignees`), so the card
 * and the notification agree on who is missing.
 */
private fun Mission.missingAssignees(checkIns: List<CheckIn>): List<String> {
  val checkedIn = checkIns.map { it.userId }.toSet()
  return assigneeIds.filter { it !in checkedIn }
}

/** The key of a volunteer missing from a mission: unique within the kind, so told once. */
private fun missedKey(missionId: String, userId: String) = "$missionId:$userId"

/**
 * The MISSED_CHECK_IN check: tells a manager, once per volunteer and mission, that a volunteer of a
 * team they manage has not checked in 10 minutes after their mission started, such as "Bob Rossi
 * hasn't checked in to Serve at the bar (12:00).", until the mission is over.
 *
 * Its manager's events are the ones they were accepted to, as only someone accepted manages a team;
 * the General missions, which have no team, have no manager to tell. A volunteer who has checked in
 * since, or a mission over, is forgotten.
 *
 * It asks [scheduler] to run again when the next mission of those teams is due, so a manager is
 * told on time even with the app closed, rather than at the engine's next periodic run.
 */
class MissedCheckIn(
    private val requests: MembershipRequestRepository,
    private val events: EventRepository,
    private val teams: TeamRepository,
    private val missions: MissionRepository,
    private val checkIns: CheckInRepository,
    private val users: UserRepository,
    private val texts: NotificationTexts,
    private val scheduler: NotificationScheduler,
) : TimedCheck {

  override val kind = NotificationKind.MISSED_CHECK_IN

  override suspend fun run(userId: String, now: Instant, seen: SeenStore): List<AppNotification> {
    // Nothing is marked until every notification is built, so a read or a text that fails loses
    // none.
    val missing =
        managedMissions(userId, now).flatMap { (event, mission) ->
          missingAt(event, mission, now, seen)
        }
    val names = users.displayNames(missing.map { it.userId })
    val shown = missing.map { notification(it, names[it.userId], now) }
    shown.forEach { seen.markShown(it.key) }
    return shown
  }

  /**
   * The missions of the teams [userId] manages in the events not over yet, each with its event. The
   * next ones not due yet are asked of [scheduler] on the way.
   */
  private suspend fun managedMissions(userId: String, now: Instant): List<Pair<Event, Mission>> =
      requests
          .getMembershipRequestsByUId(userId)
          .filterValues { it.status == MembershipRequestStatus.ACCEPTED }
          .keys
          .mapNotNull { events.getEvent(it)?.takeIf { event -> event.endAt >= now } }
          .flatMap { event ->
            val teamIds = teams.getTeamsManagedBy(event.id, userId).map { it.id }.toSet()
            missions.getMissionsByTeams(event.id, teamIds).map { event to it }
          }
          .onEach { (_, mission) ->
            if (mission.missedCheckInAt > now) scheduler.checkAt(mission.missedCheckInAt)
          }

  /**
   * The volunteers of [mission] to tell at [now] and not told yet. Its check-ins are only read once
   * it is due and not over; those over, and the volunteers checked in since, are forgotten.
   */
  private suspend fun missingAt(
      event: Event,
      mission: Mission,
      now: Instant,
      seen: SeenStore,
  ): List<Missing> {
    if (mission.missedCheckInAt > now) return emptyList()
    if (isCompleted(mission.completed, mission.endAt, now)) {
      mission.assigneeIds.forEach { seen.forget(missedKey(mission.id, it)) }
      return emptyList()
    }
    val missing = mission.missingAssignees(checkIns.observeCheckIns(event.id, mission.id).first())
    // Forgetting early can at worst tell a volunteer twice, never lose one.
    (mission.assigneeIds - missing.toSet()).forEach { seen.forget(missedKey(mission.id, it)) }
    return missing
        .filterNot { seen.wasShown(missedKey(mission.id, it)) }
        .map { Missing(event, mission, it) }
  }

  private fun notification(missing: Missing, name: String?, now: Instant): AppNotification {
    val (event, mission, userId) = missing
    val time = mission.startAt.formatTime(texts.zone)
    return AppNotification(
        kind = kind,
        key = missedKey(mission.id, userId),
        title = event.title,
        text =
            if (name != null)
                texts.string(R.string.notification_missed_check_in_text, name, mission.title, time)
            else
                texts.string(
                    R.string.notification_missed_check_in_text_unknown,
                    mission.title,
                    time,
                ),
        at = now,
        // A manager follows their team from the Volunteer app.
        target = NotificationTarget.Mission(event.id, event.title, UserMode.VOLUNTEER, mission.id),
    )
  }

  /** A volunteer, [userId], not checked in to [mission] of [event]. */
  private data class Missing(val event: Event, val mission: Mission, val userId: String)
}
