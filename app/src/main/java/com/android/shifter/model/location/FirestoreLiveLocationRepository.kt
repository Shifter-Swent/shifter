// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.team.TeamSchema
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [LiveLocationRepository] backed by Cloud Firestore, storing each shared position under its team,
 * keyed by the volunteer's uid: `/events/{eventId}/teams/{teamId}/liveLocations/{userId}`.
 *
 * The rules of [LiveLocationRepository] are enforced by `firestore.rules`, not here: this class
 * only translates what Firestore refuses into [LiveLocationRepositoryException]. In particular, the
 * volunteer's consent is read from their profile by the rules, so nothing here reads or changes it.
 *
 * [firestore] and [auth] are injected so tests can point them at the emulators. Whose position is
 * written or removed is taken from [auth] and never from an argument, so a caller cannot touch
 * another volunteer's position even by mistake; the rules refuse it either way.
 *
 * **A write is confirmed within [OPERATION_TIMEOUT_MILLIS], or reported unconfirmed.** Offline,
 * Firestore answers a write only once the server has it (#181): it neither fails nor throws
 * `UNAVAILABLE`, it simply never completes. A volunteer on site is offline often, so both
 * [shareLocation] and [removeLocation] bound that wait the way the automatic check-in does, and
 * report giving up as [LiveLocationRepositoryException.Unavailable].
 *
 * That exception then means the operation was **not confirmed**, and nothing more. Firestore keeps
 * the write or the deletion in its local queue and sends it when the connection is back, so it may
 * well reach the backend later; it is neither cancelled nor lost. A caller may retry, since the
 * rules accept the same measure twice and a removal is idempotent, but it must not read "nothing
 * was stored" or "the position is gone" into a timeout. Cancelling the caller's coroutine is a
 * different matter and still propagates as a [kotlinx.coroutines.CancellationException].
 */
class FirestoreLiveLocationRepository(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : LiveLocationRepository {

  private fun liveLocationsOf(eventId: String, teamId: String) =
      firestore
          .collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(TeamSchema.COLLECTION)
          .document(teamId)
          .collection(LiveLocationSchema.COLLECTION)

  /**
   * Writes the whole document, so the position shared before is replaced rather than merged, and
   * waits for the backend to accept it. Nothing is read first: a volunteer may write their position
   * without being allowed to read the team's.
   *
   * A [measuredAt] older than the one already stored is refused by the rules, and so arrives as a
   * [LiveLocationRepositoryException.PermissionDenied] like any other refusal; see that class for
   * why a publisher must not read a revoked consent into it.
   *
   * The wait for that acceptance is bounded by [OPERATION_TIMEOUT_MILLIS]: see the class KDoc for
   * what the resulting [LiveLocationRepositoryException.Unavailable] does and does not say about
   * the write.
   */
  override suspend fun shareLocation(
      eventId: String,
      teamId: String,
      position: GeoPosition,
      measuredAt: Instant,
  ) {
    val userId = signedInUid()
    confirmedWithin {
      liveLocationsOf(eventId, teamId)
          .document(userId)
          .set(liveLocationFields(position, measuredAt))
          .await()
    }
  }

  /**
   * Deleting a document that is not there succeeds in Firestore, which is the idempotent removal
   * [LiveLocationRepository] promises, and the rules allow the volunteer theirs even once their
   * consent is off, they have left the team or the event is over.
   *
   * Bounded by [OPERATION_TIMEOUT_MILLIS] like [shareLocation], and for the same reason: this is
   * the call the cleanup of #305 makes when a volunteer turns sharing off or leaves the team, often
   * exactly when they have no network. A timeout leaves the deletion queued, so the position may
   * disappear later rather than never; see the class KDoc.
   */
  override suspend fun removeLocation(eventId: String, teamId: String) {
    val userId = signedInUid()
    confirmedWithin { liveLocationsOf(eventId, teamId).document(userId).delete().await() }
  }

  /**
   * Listens to the team's collection, so the manager's map follows every volunteer without polling.
   * A listener error or a malformed position ends the flow with a
   * [LiveLocationRepositoryException].
   *
   * **Losing the right to read ends an open observation.** The rules bound reading by the event's
   * period and by still being the team's manager, so an hour after `endAt`, or once the organizer
   * hands the team to someone else, Firestore answers this listener with `PERMISSION_DENIED` and
   * the flow fails with [LiveLocationRepositoryException.PermissionDenied]. Firestore re-reads the
   * rules when the query's result changes rather than the moment the team document does, so the end
   * arrives with the next position written, not necessarily at once. This is the normal end of
   * every event: a screen should tell it from an unexpected failure and say that sharing has ended
   * rather than show an error.
   *
   * **That next write may never come, so the denial may never arrive.** Past `endAt` plus the
   * margin `duringEvent()` refuses the volunteers' writes as well, and a replaced manager sees
   * nothing change while their former team is idle. With no snapshot to deliver, this flow emits
   * nothing further: the listener stays open without a
   * [LiveLocationRepositoryException.PermissionDenied], while the screen goes on displaying the
   * positions it received last. No new position reaches it, only the ones already shown.
   *
   * A manager's screen therefore cannot wait for an error that may not come. It has to stop
   * observing on its own once `endAt` plus the hour has passed, and on a change of the team's
   * manager judge again whether the signed-in user may read: cancel the observation it has, and
   * start a new one only if they still may.
   *
   * Offline, the listener serves Firestore's local cache first, so the positions emitted may be
   * older than they look and carry no mark saying so. A consumer judging freshness has
   * [LiveLocation.measuredAt] and nothing else here; exposing whether a snapshot came from the
   * cache is left out on purpose.
   */
  override fun observeTeamLocations(eventId: String, teamId: String): Flow<List<LiveLocation>> =
      callbackFlow {
        val registration =
            liveLocationsOf(eventId, teamId).addSnapshotListener { snapshot, error ->
              when {
                error != null -> close(toRepositoryException(error))
                snapshot != null ->
                    try {
                      trySend(snapshot.documents.map { it.toLiveLocation() })
                    } catch (e: IllegalStateException) {
                      close(LiveLocationRepositoryException.Unknown(e))
                    }
              }
            }
        // Keeps the flow open while collected, then removes the listener so Firestore stops it.
        awaitClose { registration.remove() }
      }

  /**
   * The signed-in volunteer, whose position this is. Nobody signed in means the rules would refuse
   * the write anyway, so it is reported as the refusal it is without a round trip.
   */
  private fun signedInUid(): String =
      auth.currentUser?.uid ?: throw LiveLocationRepositoryException.PermissionDenied()

  companion object {
    /** Translates a Firestore error into the [LiveLocationRepositoryException] callers handle. */
    fun toRepositoryException(e: FirebaseFirestoreException): LiveLocationRepositoryException =
        when (e.code) {
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED -> LiveLocationRepositoryException.PermissionDenied(e)
          Code.UNAVAILABLE,
          Code.DEADLINE_EXCEEDED -> LiveLocationRepositoryException.Unavailable(e)
          else -> LiveLocationRepositoryException.Unknown(e)
        }

    /**
     * How long a write or a removal may wait for the backend before it is reported unconfirmed. The
     * ten seconds of the automatic check-in, which bounds the same offline write of #181: long
     * enough for a slow connection, and well inside the 30 s between two positions of the
     * publisher, so a sharing session cannot pile up waiting writes.
     */
    const val OPERATION_TIMEOUT_MILLIS = 10_000L

    /**
     * Runs [block] within [OPERATION_TIMEOUT_MILLIS], rethrowing its Firestore errors as
     * [LiveLocationRepositoryException] and **this** bound expiring as
     * [LiveLocationRepositoryException.Unavailable].
     *
     * [withTimeoutOrNull] answers `null` only when its own bound is the one that expired, and
     * rethrows every other cancellation, a deadline the caller imposed included. Catching
     * `TimeoutCancellationException` around a `withTimeout` cannot do that: the caller's timeout
     * carries the very same type, so their cancellation would come back from here as a write that
     * failed and their cancelled coroutine would carry on.
     *
     * [block] answers `true` rather than its own result, because `Task<Void>.await()` answers
     * `null` when it succeeds: without that marker a confirmed write would look like a timeout.
     */
    private suspend fun confirmedWithin(block: suspend () -> Unit) {
      val confirmed =
          try {
            withTimeoutOrNull(OPERATION_TIMEOUT_MILLIS.milliseconds) {
              block()
              true
            }
          } catch (e: FirebaseFirestoreException) {
            throw toRepositoryException(e)
          }
      if (confirmed == null) throw LiveLocationRepositoryException.Unavailable()
    }
  }
}
