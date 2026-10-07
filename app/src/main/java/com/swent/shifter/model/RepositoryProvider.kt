// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model

import com.swent.shifter.model.event.EventRepository
import com.swent.shifter.model.membership.MembershipRequestRepository
import com.swent.shifter.model.shift.ShiftRepository
import com.swent.shifter.model.team.TeamRepository

/**
 * The repositories the app is built from, held in one immutable container.
 *
 * It is the app-level dependency container: the composition root builds it once at start-up, and is
 * the only place that knows which implementations go in. Nothing here is mutable and nothing is
 * global, so there is no hidden state a test has to reset and no order in which things have to be
 * initialized.
 *
 * It stores repository interfaces only and names no backend, which keeps it free of any Firebase
 * dependency: the same container carries the Firestore repositories in production and fakes in a
 * test.
 *
 * It is a source of dependencies, not a service locator. ViewModels do not depend on it: the
 * composition root and the ViewModel factories take the repositories each ViewModel needs out of
 * the container and pass them to its constructor, so a ViewModel depends on those interfaces only,
 * cannot reach the repositories that are none of its business, and is built in a unit test from
 * fakes alone, without a container at all.
 */
class RepositoryProvider(
    val eventRepository: EventRepository,
    val teamRepository: TeamRepository,
    val shiftRepository: ShiftRepository,
    val membershipRequestRepository: MembershipRequestRepository,
)
