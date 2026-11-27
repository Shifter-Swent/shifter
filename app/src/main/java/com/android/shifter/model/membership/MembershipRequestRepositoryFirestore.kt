// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.Transaction
import com.swent.shifter.model.event.EventSchema
import com.swent.shifter.model.location.LiveLocationSchema
import com.swent.shifter.model.mission.MissionSchema
import com.swent.shifter.model.mission.isCompleted
import com.swent.shifter.model.shift.ShiftSchema
import com.swent.shifter.model.team.TeamSchema
import com.swent.shifter.model.team.indexManagerChange
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * [MembershipRequestRepository] backed by `events/{eventId}/membershipRequests/{userId}`.
 *
 * The document id is the volunteer's user id, so a volunteer cannot send two requests to the same
 * event and the security rules can check they only write their own.
 */
class MembershipRequestRepositoryFirestore(private val db: FirebaseFirestore) :
    MembershipRequestRepository {

  /**
   * The chosen shifts are checked before the transaction and written in it without being read, so
   * volunteers applying to the same shifts at once do not make each other's transaction run again.
   * One closed in between fails the commit, which is then reported as
   * [MembershipRequestRepositoryException.ShiftClosed].
   */
  override suspend fun apply(eventId: String, request: MembershipRequest): MembershipRequest =
      translatingErrors {
        val ref = requests(eventId).document(request.userId)
        val pending =
            request.copy(
                id = request.userId,
                status = MembershipRequestStatus.PENDING,
                shiftIds = request.shiftIds.distinct(),
            )
        requireShiftsWithinLimit(pending.shiftIds)
        val shifts = pending.shiftIds.map { shift(eventId, it) }
        failIfClosed(shifts)
        // A transaction makes check-then-create atomic, so a double tap cannot overwrite a
        // decision, and writes the request with its shifts, so neither lists the other alone. It
        // returns the existing document, or null once it wrote the pending request.
        val existing =
            reportingClosedShifts(shifts) {
              db.runTransaction { transaction ->
                    transaction.get(ref).takeIf { it.exists() }
                        ?: null.also {
                          transaction.set(ref, pending.toFirestoreMap())
                          shifts.forEach { transaction.join(it, pending.userId) }
                        }
                  }
                  .await()
            }
        existing?.toRequestOrThrow() ?: pending
      }

  override suspend fun accept(eventId: String, userId: String) {
    translatingErrors {
      val team = requestedTeam(eventId, userId)
      // The three writes commit atomically: a failed write prevents the others from being applied.
      val batch = db.batch()
      batch.update(
          requests(eventId).document(userId),
          MembershipRequestSchema.STATUS,
          MembershipRequestStatus.ACCEPTED.name,
      )
      batch.set(
          participants(eventId),
          mapOf(PARTICIPANT_IDS to FieldValue.arrayUnion(userId)),
          SetOptions.merge(),
      )
      batch.update(team, TeamSchema.MEMBER_IDS, FieldValue.arrayUnion(userId))
      batch.commit().await()
    }
  }

  override suspend fun reject(eventId: String, userId: String) {
    translatingErrors {
      val request = requests(eventId).document(userId)
      val listing = shiftsListing(eventId, userId)
      // A transaction, so the team is left alone only if it is really gone when the write lands. It
      // returns the request when it cannot be decided, or null once the decision is written.
      val undecided =
          db.runTransaction { transaction ->
                val snapshot = transaction.get(request)
                val teamId =
                    (snapshot.get(MembershipRequestSchema.TEAM_ID) as? String)?.takeIf {
                      it.isNotBlank()
                    }
                if (!snapshot.exists() || teamId == null) return@runTransaction snapshot
                val team = team(eventId, teamId)
                val teamSnapshot = transaction.get(team)
                val shifts = transaction.shiftsToLeave(eventId, snapshot, listing)
                transaction.update(
                    request,
                    MembershipRequestSchema.STATUS,
                    MembershipRequestStatus.REJECTED.name,
                )
                shifts.forEach { transaction.leave(it, userId) }
                // Merge also handles rejection before a participants document exists.
                transaction.set(
                    participants(eventId),
                    mapOf(PARTICIPANT_IDS to FieldValue.arrayRemove(userId)),
                    SetOptions.merge(),
                )
                // Rejecting an accepted request takes the volunteer out of their team as well,
                // unless the team was deleted meanwhile: then there is nothing to leave.
                if (teamSnapshot.exists()) transaction.leaveTeam(team, teamSnapshot, userId)
                null
              }
              .await()
      undecided?.let { throw undecidable(it, eventId, userId) }
    }
  }

  override suspend fun withdraw(eventId: String, userId: String) {
    translatingErrors {
      val request = requests(eventId).document(userId)
      val listing = shiftsListing(eventId, userId)
      // The rules keep an ACCEPTED request and participation in step, so the volunteer's own
      // request says whether they are a participant: they need not read the participants document.
      // A pending request may have no participants document yet, and a volunteer may not create it.
      db.runTransaction { transaction ->
            val snapshot = transaction.get(request)
            val accepted =
                snapshot.getString(MembershipRequestSchema.STATUS) ==
                    MembershipRequestStatus.ACCEPTED.name
            val team =
                snapshot
                    .getString(MembershipRequestSchema.TEAM_ID)
                    ?.takeIf { accepted }
                    ?.let { team(eventId, it) }
            // Read before any write, as a transaction requires.
            val teamSnapshot = team?.let { transaction.get(it) }
            val shifts = transaction.shiftsToLeave(eventId, snapshot, listing)
            if (accepted) {
              transaction.update(
                  participants(eventId),
                  PARTICIPANT_IDS,
                  FieldValue.arrayRemove(userId),
              )
            }
            if (team != null && teamSnapshot != null) {
              transaction.leaveTeam(team, teamSnapshot, userId)
            }
            transaction.delete(request)
            shifts.forEach { transaction.leave(it, userId) }
            null
          }
          .await()
    }
  }

  /**
   * The missions are found before the transaction, which cannot run a query: the open missions of
   * the old team that [userId] is assigned to. A mission assigned to them meanwhile keeps them, as
   * it would if it were assigned right after the move. If their request changed team meanwhile,
   * that list is outdated, so the move is refused rather than leaving them on the missions of their
   * old team.
   *
   * A refusal is returned by the transaction and thrown after it: thrown inside, a
   * [FirebaseFirestoreException] would make the SDK run the transaction again, up to five times.
   */
  override suspend fun moveToTeam(eventId: String, userId: String, teamId: String) {
    require(teamId.isNotBlank()) { "The team to move '$userId' to is blank" }
    translatingErrors {
      val now = Instant.now()
      val previousTeamId =
          requests(eventId)
              .document(userId)
              .get()
              .await()
              .getString(MembershipRequestSchema.TEAM_ID)
      val missions = openMissions(eventId, userId, previousTeamId, now)
      val refusal: String? =
          db.runTransaction { transaction ->
                transaction.move(eventId, userId, teamId, previousTeamId, missions, now)
              }
              .await()
      refusal?.let { throw MembershipRequestRepositoryException.Unknown(IllegalStateException(it)) }
    }
  }

  /** The open missions of [teamId] in [eventId] that [userId] is assigned to, at [now]. */
  private suspend fun openMissions(
      eventId: String,
      userId: String,
      teamId: String?,
      now: Instant,
  ): List<DocumentReference> =
      db.collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(MissionSchema.COLLECTION)
          .whereArrayContains(MissionSchema.ASSIGNEE_IDS, userId)
          .get()
          .await()
          .documents
          .filter { it.isOpenMissionOf(teamId, now) }
          .map { it.reference }

  /**
   * Moves [userId] to [teamId] in this transaction, from [previousTeamId], the team their request
   * named when [missions] were found: out of that team and off those of its [missions] still open.
   * Every read comes before the first write, as a transaction requires. Returns why the move is
   * refused, or null once it is written.
   */
  private fun Transaction.move(
      eventId: String,
      userId: String,
      teamId: String,
      previousTeamId: String?,
      missions: List<DocumentReference>,
      now: Instant,
  ): String? {
    val request = requests(eventId).document(userId)
    val snapshot = get(request)
    if (
        snapshot.getString(MembershipRequestSchema.STATUS) != MembershipRequestStatus.ACCEPTED.name
    ) {
      return "'$userId' is not an accepted volunteer of event '$eventId'"
    }
    val target = team(eventId, teamId)
    val targetSnapshot = get(target)
    if (!targetSnapshot.exists()) return "Team '$teamId' of event '$eventId' does not exist"
    val from = snapshot.getString(MembershipRequestSchema.TEAM_ID)
    if (from == teamId) {
      rejoin(target, targetSnapshot, userId)
      return null
    }
    if (from != previousTeamId) return "The team of '$userId' changed meanwhile, try again"
    val previous = from?.let { team(eventId, it) }
    val previousSnapshot = previous?.let { get(it) }
    if (previousSnapshot?.getString(TeamSchema.MANAGER_ID) == userId) {
      return "'$userId' manages their team and cannot be moved"
    }
    // Read again, so a mission deleted, given to another team or closed meanwhile is left alone.
    val assigned = missions.filter { get(it).isOpenMissionOf(from, now) }
    if (previous != null && previousSnapshot?.exists() == true) {
      leaveTeam(previous, previousSnapshot, userId)
    }
    update(target, TeamSchema.MEMBER_IDS, FieldValue.arrayUnion(userId))
    update(request, MembershipRequestSchema.TEAM_ID, teamId)
    assigned.forEach { update(it, MissionSchema.ASSIGNEE_IDS, FieldValue.arrayRemove(userId)) }
    return null
  }

  /**
   * A transaction, so the shifts the volunteer leaves are those their request lists when it
   * changes, and those whose volunteers list them. The chosen shifts are checked before it and
   * joined without being read, as in [apply]. A refusal is returned by the transaction and thrown
   * after it, as in [moveToTeam].
   */
  override suspend fun updateShifts(
      eventId: String,
      userId: String,
      shiftIds: List<String>,
      availability: List<AvailabilitySlot>,
  ) {
    require(availability.isNotEmpty()) { "The availability of '$userId' is empty" }
    val chosen = shiftIds.distinct()
    requireShiftsWithinLimit(chosen)
    translatingErrors {
      val shifts = chosen.map { shift(eventId, it) }
      failIfClosed(shifts)
      val listing = shiftsListing(eventId, userId)
      val refusal =
          reportingClosedShifts(shifts) {
            db.runTransaction { transaction ->
                  transaction.changeShifts(eventId, userId, chosen, availability, listing)
                }
                .await()
          }
      refusal?.let { throw it }
    }
  }

  /**
   * Replaces the shifts of [userId]'s pending request in this transaction: they leave the shifts it
   * no longer lists and those [listing] found that are not chosen, unless gone, and join the new
   * ones. Returns the error that refuses it, or null once written.
   */
  private fun Transaction.changeShifts(
      eventId: String,
      userId: String,
      shiftIds: List<String>,
      availability: List<AvailabilitySlot>,
      listing: List<DocumentReference>,
  ): MembershipRequestRepositoryException? {
    val request = requests(eventId).document(userId)
    val snapshot = get(request)
    if (
        snapshot.getString(MembershipRequestSchema.STATUS) != MembershipRequestStatus.PENDING.name
    ) {
      return MembershipRequestRepositoryException.Unknown(
          IllegalStateException("'$userId' has no pending request for event '$eventId'")
      )
    }
    val before = snapshot.shiftIds()
    val chosen = shiftIds.map { shift(eventId, it).path }.toSet()
    val left = shiftsToLeave(eventId, snapshot, listing).filter { it.path !in chosen }
    update(
        request,
        mapOf(
            MembershipRequestSchema.SHIFT_IDS to shiftIds,
            MembershipRequestSchema.AVAILABILITY to availability.map { it.toFirestoreMap() },
        ),
    )
    (shiftIds - before.toSet()).forEach { join(shift(eventId, it), userId) }
    left.forEach { leave(it, userId) }
    return null
  }

  /** Refuses more shifts than a request can hold, see [MAX_SHIFTS]. */
  private fun requireShiftsWithinLimit(shiftIds: List<String>) {
    require(shiftIds.size <= MAX_SHIFTS) {
      "A request chooses at most $MAX_SHIFTS shifts, not ${shiftIds.size}"
    }
  }

  /**
   * Throws [MembershipRequestRepositoryException.ShiftClosed] if one of [shifts] no longer exists,
   * read from the server, all at once.
   */
  private suspend fun failIfClosed(shifts: List<DocumentReference>) {
    val closed = coroutineScope {
      shifts
          .map { async { it.takeUnless { shift -> shift.get(Source.SERVER).await().exists() } } }
          .awaitAll()
          .firstNotNullOfOrNull { it }
    }
    if (closed != null) throw MembershipRequestRepositoryException.ShiftClosed(closed.id)
  }

  /**
   * Runs [block], a transaction joining [shifts] without reading them: one closed meanwhile fails
   * its commit with NOT_FOUND, then reported as [MembershipRequestRepositoryException.ShiftClosed].
   */
  private suspend fun <T> reportingClosedShifts(
      shifts: List<DocumentReference>,
      block: suspend () -> T,
  ): T =
      try {
        block()
      } catch (e: FirebaseFirestoreException) {
        if (e.code == Code.NOT_FOUND) {
          // Failing itself, e.g. offline, the check must not hide why the commit failed.
          try {
            failIfClosed(shifts)
          } catch (_: FirebaseFirestoreException) {}
        }
        throw e
      }

  /**
   * The shifts of [eventId] whose volunteers list [userId], found before the transaction, which
   * cannot run a query. A request may stop listing a shift its volunteer is still in, so its
   * `shiftIds` alone could leave them there.
   */
  private suspend fun shiftsListing(eventId: String, userId: String): List<DocumentReference> =
      db.collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(ShiftSchema.COLLECTION)
          .whereArrayContains(ShiftSchema.VOLUNTEER_IDS, userId)
          .get()
          .await()
          .documents
          .map { it.reference }

  /**
   * The shifts the volunteer leaves, read in this transaction: those [listing] found, and those the
   * request in [snapshot] chose, in case one was joined since. A shift that is gone is skipped: the
   * organizer can close one once they took its volunteers out, which no write can then update.
   */
  private fun Transaction.shiftsToLeave(
      eventId: String,
      snapshot: DocumentSnapshot,
      listing: List<DocumentReference>,
  ): List<DocumentReference> =
      existing((listing + snapshot.shiftIds().map { shift(eventId, it) }).distinctBy { it.path })

  private fun Transaction.existing(shifts: List<DocumentReference>): List<DocumentReference> =
      shifts.filter {
        get(it).exists()
      }

  private fun Transaction.join(shift: DocumentReference, userId: String) {
    update(shift, ShiftSchema.VOLUNTEER_IDS, FieldValue.arrayUnion(userId))
  }

  private fun Transaction.leave(shift: DocumentReference, userId: String) {
    update(shift, ShiftSchema.VOLUNTEER_IDS, FieldValue.arrayRemove(userId))
  }

  /**
   * The shift ids a request lists, at most [MAX_SHIFTS] as the rules allow. Only epoch milliseconds
   * are kept, as `shiftIdOf` makes them: the rules only check that `shiftIds` is a list, and an id
   * such as "" or "a/b" would make no document path.
   */
  private fun DocumentSnapshot.shiftIds(): List<String> =
      (get(MembershipRequestSchema.SHIFT_IDS) as? List<*>)
          .orEmpty()
          .filterIsInstance<String>()
          .filter { it.toLongOrNull() != null }
          .take(MAX_SHIFTS)

  private fun shift(eventId: String, shiftId: String): DocumentReference =
      db.collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(ShiftSchema.COLLECTION)
          .document(shiftId)

  override suspend fun getMembershipRequest(eventId: String, userId: String): MembershipRequest? =
      translatingErrors {
        requests(eventId).document(userId).get().await().takeIf { it.exists() }?.toRequestOrThrow()
      }

  override suspend fun getMembershipRequestsByEId(eventId: String): List<MembershipRequest> =
      translatingErrors {
        requests(eventId).get().await().documents.mapNotNull {
          runCatching { it.toMembershipRequest() }
              .getOrElse { e -> if (e is IllegalStateException) null else throw e }
        }
      }

  override suspend fun getMembershipRequestsByUId(userId: String): Map<String, MembershipRequest> =
      translatingErrors {
        db.collectionGroup(MembershipRequestSchema.COLLECTION)
            .whereEqualTo(MembershipRequestSchema.USER_ID, userId)
            .get()
            .await()
            .documents
            .associate { document ->
              val eventId =
                  document.reference.parent.parent?.id
                      ?: throw MembershipRequestRepositoryException.Unknown(
                          IllegalStateException(
                              "Membership request '${document.id}' has no parent event"
                          )
                      )
              eventId to document.toRequestOrThrow()
            }
      }

  private fun requests(eventId: String): CollectionReference =
      db.collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(MembershipRequestSchema.COLLECTION)

  private fun participants(eventId: String): DocumentReference =
      db.collection(PARTICIPANTS_COLLECTION).document(eventId)

  private fun team(eventId: String, teamId: String): DocumentReference =
      db.collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(TeamSchema.COLLECTION)
          .document(teamId)

  /**
   * Takes [userId] out of [team], read as [snapshot] earlier in this transaction: out of its
   * members, out of its management when they lead it, or they would keep managing a team of an
   * event they no longer take part in, with the index of the event's managers, and out of the
   * positions shared with its manager. Nothing is written to the team itself when they are in
   * neither: the rules refuse a withdrawal that changes nothing on the team, which would fail the
   * whole transaction.
   *
   * The position goes in the same commit as the membership, so the manager never keeps reading the
   * position of someone who has left (#305). It is deleted whether one is stored or not: a delete
   * needs no read, which a transaction would otherwise have to do before every write, and deleting
   * a document that is not there is a no-op in Firestore. That also takes out a position left
   * behind by a `memberIds` written directly, outside this repository.
   */
  private fun Transaction.leaveTeam(
      team: DocumentReference,
      snapshot: DocumentSnapshot,
      userId: String,
  ) {
    val memberIds = snapshot.get(TeamSchema.MEMBER_IDS) as? List<*> ?: emptyList<Any>()
    if (userId in memberIds) update(team, TeamSchema.MEMBER_IDS, FieldValue.arrayRemove(userId))
    if (snapshot.getString(TeamSchema.MANAGER_ID) == userId) {
      update(team, TeamSchema.MANAGER_ID, null)
      indexManagerChange(team, previous = userId, next = null)
    }
    delete(team.collection(LiveLocationSchema.COLLECTION).document(userId))
  }

  /**
   * Puts [userId] back among the members of [team], read as [snapshot] earlier in this transaction,
   * when their request names it but they are missing from its `memberIds`. Nothing is written when
   * they are in it already, or when they manage it, since a manager is never one of its members.
   */
  private fun Transaction.rejoin(
      team: DocumentReference,
      snapshot: DocumentSnapshot,
      userId: String,
  ) {
    val memberIds = snapshot.get(TeamSchema.MEMBER_IDS) as? List<*> ?: emptyList<Any>()
    if (userId in memberIds || snapshot.getString(TeamSchema.MANAGER_ID) == userId) return
    update(team, TeamSchema.MEMBER_IDS, FieldValue.arrayUnion(userId))
  }

  /**
   * The team [userId] applied to in [eventId], read from their request. Only an accepted request
   * changes team, when [moveToTeam] moves the volunteer, so it can be read before the batch that
   * decides the request.
   */
  private suspend fun requestedTeam(eventId: String, userId: String): DocumentReference {
    val snapshot = requests(eventId).document(userId).get().await()
    if (!snapshot.exists()) throw undecidable(snapshot, eventId, userId)
    return team(eventId, snapshot.toRequestOrThrow().teamId)
  }

  /**
   * Why the request in [snapshot] cannot be decided: it does not exist, or it does not match the
   * schema. Both are reported as [MembershipRequestRepositoryException.Unknown].
   */
  private fun undecidable(
      snapshot: DocumentSnapshot,
      eventId: String,
      userId: String,
  ): MembershipRequestRepositoryException {
    if (!snapshot.exists()) {
      return MembershipRequestRepositoryException.Unknown(
          NoSuchElementException("No request from '$userId' for event '$eventId'")
      )
    }
    snapshot.toRequestOrThrow()
    return MembershipRequestRepositoryException.Unknown(
        IllegalStateException("Membership request '${snapshot.id}' has no team")
    )
  }

  companion object {
    private const val PARTICIPANTS_COLLECTION = "eventParticipants"
    private const val PARTICIPANT_IDS = "participantIds"

    /**
     * The most shifts a request chooses, as the rules allow: a product bound, more than 40 days of
     * 2 h slots, that keeps the commits leaving them all well within Firestore's limits per
     * request.
     */
    private const val MAX_SHIFTS = 490

    /**
     * Translates a Firestore error into the [MembershipRequestRepositoryException] callers handle.
     */
    internal fun toRepositoryException(
        e: FirebaseFirestoreException
    ): MembershipRequestRepositoryException =
        when (e.code) {
          Code.PERMISSION_DENIED,
          Code.UNAUTHENTICATED -> MembershipRequestRepositoryException.PermissionDenied(e)
          Code.UNAVAILABLE,
          Code.DEADLINE_EXCEEDED -> MembershipRequestRepositoryException.Unavailable(e)
          else -> MembershipRequestRepositoryException.Unknown(e)
        }

    /**
     * Whether this mission exists, belongs to [teamId] and is still open at [now]: neither closed
     * by hand nor over. A move leaves the volunteer on the others, whose attendance is built from
     * their assignees, so the missions they already worked keep showing their check-in. A mission
     * without an end is kept open, as the mapper would reject it anyway.
     */
    private fun DocumentSnapshot.isOpenMissionOf(teamId: String?, now: Instant): Boolean {
      if (!exists() || getString(MissionSchema.TEAM_ID) != teamId) return false
      val endAt = getTimestamp(MissionSchema.END_AT)?.toInstant() ?: return true
      return !isCompleted(getBoolean(MissionSchema.COMPLETED) == true, endAt, now)
    }

    private inline fun <T> translatingErrors(block: () -> T): T =
        try {
          block()
        } catch (e: FirebaseFirestoreException) {
          throw toRepositoryException(e)
        }

    /**
     * Maps a stored document, reporting one that does not match the schema as
     * [MembershipRequestRepositoryException.Unknown]. The mapper signals it with an
     * [IllegalStateException], which is only caught around this pure call: catching it around an
     * `await()` would also swallow coroutine cancellation, a subclass of it.
     */
    private fun DocumentSnapshot.toRequestOrThrow(): MembershipRequest =
        try {
          toMembershipRequest()
        } catch (e: IllegalStateException) {
          throw MembershipRequestRepositoryException.Unknown(e)
        }
  }
}
