// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import com.google.firebase.firestore.DocumentReference
import com.swent.shifter.model.event.EventSchema
import java.util.UUID
import kotlinx.coroutines.tasks.await

/**
 * Parent events for the tests of the event subcollections (teams, shifts, missions).
 *
 * `firestore.rules` only lets the organizer of an existing event write those subcollections, so a
 * test first creates an event it owns. The document holds only `organizerId`: the subcollection
 * rules read nothing else, and a minimal document shows these tests do not depend on the event's
 * other fields.
 *
 * Delete the event after its subcollection documents: once it is gone, nobody is its organizer and
 * the rules refuse to delete what is left under it.
 */
object ScratchEvents {

  /** Creates an event owned by the signed-in user under a fresh id and returns its document. */
  suspend fun create(): DocumentReference {
    val user =
        checkNotNull(FirestoreEmulator.auth.currentUser) { "Sign in before creating an event" }
    val event =
        FirestoreEmulator.firestore
            .collection(EventSchema.COLLECTION)
            .document("event-" + UUID.randomUUID())
    event.set(mapOf(EventSchema.ORGANIZER_ID to user.uid)).await()
    return event
  }
}
