// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.event

import kotlinx.coroutines.awaitCancellation

/**
 * In-memory [EventRepository] for unit tests.
 *
 * @property failure when set, every call throws it, to simulate a backend error.
 * @property unconfirmedWrites when true, [createEvent] stores the event but never returns, like
 *   Firestore offline: the write is queued locally and never confirmed by the server.
 */
class FakeEventRepository(
    var failure: Exception? = null,
    var unconfirmedWrites: Boolean = false,
) : EventRepository {

  /** Every event persisted so far, in creation order. */
  val events = mutableListOf<Event>()

  override suspend fun createEvent(event: Event): Event {
    failure?.let { throw it }
    val persisted =
        event.copy(
            id = event.id.ifEmpty { "event-${events.size + 1}" },
            joinCode = "CODE${events.size + 1}",
        )
    events.removeAll { it.id == persisted.id }
    events += persisted
    if (unconfirmedWrites) awaitCancellation()
    return persisted
  }

  override suspend fun getEvent(eventId: String): Event? {
    failure?.let { throw it }
    return events.firstOrNull { it.id == eventId }
  }

  override suspend fun getEventByJoinCode(joinCode: String): Event? {
    failure?.let { throw it }
    return events.firstOrNull { it.joinCode == joinCode }
  }

  override suspend fun getEventsByOrganizer(organizerId: String): List<Event> {
    failure?.let { throw it }
    return events.filter { it.organizerId == organizerId }
  }
}
