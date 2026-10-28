// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model

import com.swent.shifter.model.authentication.AuthRepository
import com.swent.shifter.model.checkin.CheckInRepository
import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.mission.MissionRepository
import com.swent.shifter.model.team.TeamRepository
import com.swent.shifter.model.user.UserRepository

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
)
