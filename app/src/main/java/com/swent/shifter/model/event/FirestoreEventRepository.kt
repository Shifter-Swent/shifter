// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.event

import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random
import kotlinx.coroutines.tasks.await

/**
 * [EventRepository] backed by Cloud Firestore, storing events under `/events/{eventId}`.
 *
 * All Firebase types stay inside this class and the [toEvent] / [toFirestoreMap] mappers: callers
 * only ever see domain models. [firestore] is injected so tests can point it at the emulator.
 *
 * @param random source of randomness for join codes; inject a seeded [Random] to make collisions
 *   reproducible in tests.
 */
class FirestoreEventRepository(
    private val firestore: FirebaseFirestore,
    private val random: Random = Random.Default,
) : EventRepository {

  private val events
    get() = firestore.collection(EventSchema.COLLECTION)

  /**
   * Writes [event] under a freshly generated document id and join code.
   *
   * [Event.createdAt] is persisted as the caller supplied it: the repository does not invent time,
   * which keeps writes deterministic and testable.
   */
  override suspend fun createEvent(event: Event): Event {
    val document = events.document()
    val persisted = event.copy(id = document.id, joinCode = generateUnusedJoinCode())
    document.set(persisted.toFirestoreMap()).await()
    return persisted
  }

  override suspend fun getEvent(eventId: String): Event? =
      events.document(eventId).get().await().takeIf { it.exists() }?.toEvent()

  override suspend fun getEventByJoinCode(joinCode: String): Event? =
      events
          .whereEqualTo(EventSchema.JOIN_CODE, joinCode)
          .limit(1)
          .get()
          .await()
          .documents
          .firstOrNull()
          ?.toEvent()

  override suspend fun getEventsByOrganizer(organizerId: String): List<Event> =
      events.whereEqualTo(EventSchema.ORGANIZER_ID, organizerId).get().await().documents.map {
        it.toEvent()
      }

  /**
   * Draws join codes until one is free, so two events never share a code.
   *
   * With a 31-character alphabet and 6 characters, a collision is very unlikely; retrying a few
   * times is enough and avoids a reservation mechanism. The check and the write are not atomic, so
   * two events created at the very same instant could in theory take the same code: acceptable
   * here, and a Firestore transaction or a dedicated `/joinCodes` collection is the fix if it ever
   * matters.
   */
  private suspend fun generateUnusedJoinCode(): String {
    repeat(JOIN_CODE_ATTEMPTS) {
      val candidate = randomJoinCode()
      if (getEventByJoinCode(candidate) == null) return candidate
    }
    throw IllegalStateException(
        "Could not find an unused join code after $JOIN_CODE_ATTEMPTS attempts"
    )
  }

  private fun randomJoinCode(): String =
      String(
          CharArray(JOIN_CODE_LENGTH) {
            JOIN_CODE_ALPHABET[random.nextInt(JOIN_CODE_ALPHABET.length)]
          }
      )

  private companion object {
    /** Digits and upper-case letters, without the pairs users confuse: 0/O, 1/I/L. */
    const val JOIN_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val JOIN_CODE_LENGTH = 6
    const val JOIN_CODE_ATTEMPTS = 5
  }
}
