// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.event

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlin.random.Random
import kotlinx.coroutines.tasks.await

/**
 * [EventRepository] backed by Cloud Firestore, storing events under `/events/{eventId}` and their
 * join codes under `/joinCodes/{code}`.
 *
 * All Firebase types stay inside this class and the [toEvent] / [toFirestoreMap] mappers: callers
 * only ever see domain models. [firestore] is injected so tests can point it at the emulator.
 * Firestore errors are rethrown as [EventRepositoryException].
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

  private val joinCodes
    get() = firestore.collection(JoinCodeSchema.COLLECTION)

  /**
   * Writes [event] under a freshly generated join code, and under [Event.id] when it is set or a
   * freshly generated document id otherwise.
   *
   * The code is reserved in `/joinCodes/{code}` in the same transaction as the event, so two events
   * never share a code: a code already taken is detected inside the transaction and another one is
   * drawn. With a 31-character alphabet and 6 characters, a few attempts are enough. An event
   * already stored under [Event.id], as when a create is retried, keeps its code.
   *
   * A transaction needs the server: offline, this fails with [EventRepositoryException.Unavailable]
   * instead of being queued until the connection is back.
   *
   * [Event.createdAt] is persisted as the caller supplied it: the repository does not invent time,
   * which keeps writes deterministic and testable.
   */
  override suspend fun createEvent(event: Event): Event = translatingErrors {
    val document = if (event.id.isEmpty()) events.document() else events.document(event.id)
    repeat(JOIN_CODE_ATTEMPTS) {
      val persisted = reserve(event.copy(id = document.id, joinCode = randomJoinCode()))
      if (persisted != null) return@translatingErrors persisted
    }
    throw IllegalStateException(
        "Could not find an unused join code after $JOIN_CODE_ATTEMPTS attempts"
    )
  }

  override suspend fun getEvent(eventId: String): Event? = translatingErrors {
    events.document(eventId).get().await().takeIf { it.exists() }?.toEvent()
  }

  override suspend fun getEventByJoinCode(joinCode: String): Event? = translatingErrors {
    // Never drawn by the generator, so no event uses it; some, such as "..", are not even a valid
    // document id.
    if (!JoinCode.isValid(joinCode)) return@translatingErrors null
    val eventId =
        joinCodes.document(joinCode).get().await().getString(JoinCodeSchema.EVENT_ID)
            ?: return@translatingErrors null
    getEvent(eventId)
  }

  override suspend fun getEventsByOrganizer(organizerId: String): List<Event> = translatingErrors {
    events.whereEqualTo(EventSchema.ORGANIZER_ID, organizerId).get().await().documents.map {
      it.toEvent()
    }
  }

  /**
   * Writes [event] and reserves its join code in one transaction, unless the code is already taken.
   *
   * An event already stored under [Event.id], such as a create retried after a timeout that went
   * through, keeps the code it has: that code is reserved for it already, and the rules never let
   * an event's code change.
   *
   * @return the event as written, or null when another event already uses its join code.
   */
  private suspend fun reserve(event: Event): Event? {
    val document = events.document(event.id)
    val code = joinCodes.document(event.joinCode)
    return firestore
        .runTransaction { transaction ->
          val stored = transaction.get(document).getString(EventSchema.JOIN_CODE)
          when {
            stored != null ->
                event.copy(joinCode = stored).also {
                  transaction.set(document, it.toFirestoreMap())
                }
            transaction.get(code).exists() -> null
            else -> {
              transaction.set(document, event.toFirestoreMap())
              transaction.set(code, mapOf(JoinCodeSchema.EVENT_ID to event.id))
              event
            }
          }
        }
        .await()
  }

  private fun randomJoinCode(): String =
      String(
          CharArray(JoinCode.LENGTH) { JoinCode.ALPHABET[random.nextInt(JoinCode.ALPHABET.length)] }
      )

  companion object {
    /** Translates a Firestore error into the [EventRepositoryException] callers handle. */
    fun toRepositoryException(e: FirebaseFirestoreException): EventRepositoryException =
        when (e.code) {
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED -> EventRepositoryException.PermissionDenied(e)
          Code.UNAVAILABLE,
          Code.DEADLINE_EXCEEDED -> EventRepositoryException.Unavailable(e)
          else -> EventRepositoryException.Unknown(e)
        }

    private inline fun <T> translatingErrors(block: () -> T): T =
        try {
          block()
        } catch (e: FirebaseFirestoreException) {
          throw toRepositoryException(e)
        }

    private const val JOIN_CODE_ATTEMPTS = 5
  }
}
