// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.android.shifter.model.event

/**
 * Read and write access to the events, independent of any backend.
 *
 * ViewModels depend on this interface and never on an implementation, so the Firestore repository
 * can be replaced by a fake in unit tests.
 */
interface EventRepository {

  /**
   * Persists [event] and returns it with the fields the backend assigns, such as its id and join
   * code.
   */
  suspend fun createEvent(event: Event): Event

  /** Returns the event identified by [eventId], or null when no such event exists. */
  suspend fun getEvent(eventId: String): Event?

  /** Returns the event volunteers join with [joinCode], or null when no event uses that code. */
  suspend fun getEventByJoinCode(joinCode: String): Event?

  /** Returns every event owned by [organizerId], or an empty list when there is none. */
  suspend fun getEventsByOrganizer(organizerId: String): List<Event>
}
