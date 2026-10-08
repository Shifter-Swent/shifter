// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.event

/**
 * In-memory [EventRepository] for unit tests.
 *
 * @property failure when set, every call throws it, to simulate a backend error.
 */
class FakeEventRepository(var failure: Exception? = null) : EventRepository {

  /** Every event persisted so far, in creation order. */
  val events = mutableListOf<Event>()

  override suspend fun createEvent(event: Event): Event {
    failure?.let { throw it }
    val persisted = event.copy(id = "event-${events.size + 1}", joinCode = "CODE${events.size + 1}")
    events += persisted
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

  override suspend fun getEventsByMember(userId: String): List<Event> {
    failure?.let { throw it }
    return events.filter { userId in it.memberIds }
  }
}
