// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.team.TeamSchema
import java.time.Instant
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

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
   */
  override suspend fun shareLocation(
      eventId: String,
      teamId: String,
      position: GeoPosition,
      measuredAt: Instant,
  ) {
    val userId = signedInUid()
    translatingErrors {
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
   */
  override suspend fun removeLocation(eventId: String, teamId: String) {
    val userId = signedInUid()
    translatingErrors { liveLocationsOf(eventId, teamId).document(userId).delete().await() }
  }

  /**
   * Listens to the team's collection, so the manager's map follows every volunteer without polling.
   * A listener error or a malformed position ends the flow with a
   * [LiveLocationRepositoryException].
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

    /** Runs [block], rethrowing its Firestore errors as [LiveLocationRepositoryException]. */
    private inline fun <T> translatingErrors(block: () -> T): T =
        try {
          block()
        } catch (e: FirebaseFirestoreException) {
          throw toRepositoryException(e)
        }
  }
}
