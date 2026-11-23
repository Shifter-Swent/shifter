// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model

import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.chat.ChatRepository
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.location.LiveLocationRepository
import com.swent.shifter.model.location.LocalSharingConsent
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.notification.NotificationInbox
import com.swent.shifter.model.notification.NotificationPreferences
import com.swent.shifter.model.shift.ShiftRepository
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.time.DeviceMinuteClock
import com.swent.shifter.model.time.MinuteClock
import com.swent.shifter.model.user.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The repositories the app is built from, held in one immutable container.
 *
 * It stores repository interfaces only and names no backend, so it needs no Firebase:
 * [RepositoryProviderFactory] fills it with the Firestore repositories in production, and a test
 * fills it with fakes.
 *
 * It is a source of dependencies, not a service locator. ViewModels depend on the individual
 * repositories they need, never on this container: whoever builds a ViewModel takes those
 * repositories out and passes them to its constructor, so a ViewModel cannot reach the repositories
 * that are none of its business and a unit test builds it from fakes alone.
 */
class RepositoryProvider(
    val authRepository: AuthRepository,
    val eventRepository: EventRepository,
    val userRepository: UserRepository,
    val membershipRequestRepository: MembershipRequestRepository,
    val missionRepository: MissionRepository,
    val teamRepository: TeamRepository,
    val checkInRepository: CheckInRepository,
    val chatRepository: ChatRepository,
    val liveLocationRepository: LiveLocationRepository,
    /** Each user's notification inbox, read and emptied by the notification engine (#614). */
    val notificationInbox: NotificationInbox,
    val shiftRepository: ShiftRepository,
    /**
     * The volunteer's just-made location-sharing decision, shared between Settings and the
     * foreground publisher so turning sharing off stops GPS at once (#377). In memory, not a
     * backend, so it is held here with a default rather than built by [RepositoryProviderFactory].
     */
    val localSharingConsent: LocalSharingConsent = LocalSharingConsent(),
    /**
     * The one clock the screens follow, so they share a single minute timer (#518). It reads the
     * device and starts ticking only while collected, so it is held here with a default, like
     * [localSharingConsent]; a test passes a fake to move time by hand.
     */
    val minuteClock: MinuteClock =
        DeviceMinuteClock(CoroutineScope(SupervisorJob() + Dispatchers.Default)),
    /**
     * The kinds of notifications each user turned off, read by the notification engine (#614). The
     * choices live on the profile, so they are read from [userRepository] rather than built by
     * [RepositoryProviderFactory].
     */
    val notificationPreferences: NotificationPreferences = NotificationPreferences { userId ->
      userRepository.observeDisabledNotifications(userId)
    },
)
