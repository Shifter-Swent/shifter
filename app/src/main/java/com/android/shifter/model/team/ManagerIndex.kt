// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.team

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.WriteBatch

/**
 * Names of the index of the managers of an event: `/events/{eventId}/managers/{uid}` lists the
 * teams of the event that uid manages.
 *
 * It mirrors [TeamSchema.MANAGER_ID] of the event's teams, so `firestore.rules` can tell whether
 * someone manages any team of the event, which a rule cannot ask the teams themselves (#426). It is
 * written in the same commit as the team, and never read by the app.
 */
internal object ManagerIndexSchema {

  /** The `/events/{eventId}/managers/{uid}` subcollection, one document per manager. */
  const val COLLECTION = "managers"

  /** The ids of the teams the user manages; empty once they manage none. */
  const val TEAM_IDS = "teamIds"
}

/** The index entry of [managerId] in the event of [team]. */
private fun managerEntry(team: DocumentReference, managerId: String): DocumentReference =
    checkNotNull(team.parent.parent) { "A team belongs to an event" }
        .collection(ManagerIndexSchema.COLLECTION)
        .document(managerId)

/**
 * Mirrors in this transaction the change of manager of [team] from [previous] to [next]: the team
 * leaves the entry of [previous] and joins that of [next]. Nothing is written when they are the
 * same, as the rules refuse an index write that changes nothing about the team.
 */
internal fun Transaction.indexManagerChange(
    team: DocumentReference,
    previous: String?,
    next: String?,
) {
  if (previous == next) return
  previous?.let { set(managerEntry(team, it), removing(team), SetOptions.merge()) }
  next?.let { set(managerEntry(team, it), adding(team), SetOptions.merge()) }
}

/** Indexes [managerId] as the manager of the new [team], in this batch. */
internal fun WriteBatch.indexManager(team: DocumentReference, managerId: String) {
  set(managerEntry(team, managerId), adding(team), SetOptions.merge())
}

/** Merged rather than rewritten, so the other teams the manager leads stay listed. */
private fun adding(team: DocumentReference) =
    mapOf(ManagerIndexSchema.TEAM_IDS to FieldValue.arrayUnion(team.id))

private fun removing(team: DocumentReference) =
    mapOf(ManagerIndexSchema.TEAM_IDS to FieldValue.arrayRemove(team.id))
