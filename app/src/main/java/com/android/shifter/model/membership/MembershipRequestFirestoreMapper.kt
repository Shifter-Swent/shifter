// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.membership

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

/**
 * Names of the membership requests subcollection and of every field of a request document.
 *
 * [MembershipRequest.id] and the event id are deliberately absent: the first is the document id,
 * the second the id of the parent document, so the path already carries both and storing them would
 * be a second source of truth.
 */
internal object MembershipRequestSchema {

  /** The `/events/{eventId}/membershipRequests/{requestId}` subcollection. */
  const val COLLECTION = "membershipRequests"

  const val USER_ID = "userId"
  const val PREFERRED_TEAM_IDS = "preferredTeamIds"
  const val AVAILABILITY = "availability"
  const val STATUS = "status"
  const val CREATED_AT = "createdAt"

  /** Fields of each map in the [AVAILABILITY] array. */
  object SlotFields {
    const val START_AT = "startAt"
    const val END_AT = "endAt"
  }
}

/** The document body written to `/events/{eventId}/membershipRequests/{requestId}`. */
internal fun MembershipRequest.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        MembershipRequestSchema.USER_ID to userId,
        MembershipRequestSchema.PREFERRED_TEAM_IDS to preferredTeamIds,
        MembershipRequestSchema.AVAILABILITY to availability.map { it.toFirestoreMap() },
        MembershipRequestSchema.STATUS to status.name,
        MembershipRequestSchema.CREATED_AT to createdAt.toFirestoreTimestamp(),
    )

/**
 * Rebuilds the [MembershipRequest] stored in this document, taking [MembershipRequest.id] from the
 * document id.
 *
 * Throws [IllegalStateException] when a required field is missing or has an unexpected type: a
 * document that cannot be mapped is a schema problem, which must not be mistaken for "no request".
 * Hence [DocumentSnapshot.get] everywhere rather than `getString`/`getTimestamp`, which raise a
 * bare Firebase `RuntimeException` on a type mismatch.
 */
internal fun DocumentSnapshot.toMembershipRequest(): MembershipRequest =
    MembershipRequest(
        id = id,
        userId = requireString(MembershipRequestSchema.USER_ID),
        preferredTeamIds = requirePreferredTeamIds(),
        availability = requireAvailability(),
        status = requireStatus(),
        createdAt = requireInstant(MembershipRequestSchema.CREATED_AT),
    )

private fun AvailabilitySlot.toFirestoreMap(): Map<String, Any?> =
    mapOf(
        MembershipRequestSchema.SlotFields.START_AT to startAt.toFirestoreTimestamp(),
        MembershipRequestSchema.SlotFields.END_AT to endAt.toFirestoreTimestamp(),
    )

/**
 * An unknown name is rejected rather than mapped to a default: the status decides whether a
 * volunteer takes part in the event, and [MembershipRequestStatus] has no catch-all member, so
 * guessing one would either enrol someone an organizer rejected or drop someone they accepted.
 */
private fun DocumentSnapshot.requireStatus(): MembershipRequestStatus {
  val name = requireString(MembershipRequestSchema.STATUS)
  return MembershipRequestStatus.entries.firstOrNull { it.name == name }
      ?: invalid(MembershipRequestSchema.STATUS)
}

/**
 * Preference only, so an absent field and an empty array both mean "no preferred team". A null is
 * not accepted for it: the write side always stores an array, so a null is a malformed document
 * rather than a volunteer with no preference.
 *
 * The order is the order of preference and is kept as stored. A malformed entry is rejected rather
 * than skipped: dropping one would silently change which team the volunteer asked for first.
 */
private fun DocumentSnapshot.requirePreferredTeamIds(): List<String> {
  if (!contains(MembershipRequestSchema.PREFERRED_TEAM_IDS)) return emptyList()
  val entries =
      get(MembershipRequestSchema.PREFERRED_TEAM_IDS) as? List<*>
          ?: invalid(MembershipRequestSchema.PREFERRED_TEAM_IDS)
  return entries.map { it as? String ?: invalid(MembershipRequestSchema.PREFERRED_TEAM_IDS) }
}

/**
 * Required, and required to hold at least one slot: an organizer cannot schedule a volunteer who
 * declared no time window, so an absent, null or empty field is a malformed document rather than a
 * request with nothing to schedule.
 *
 * A malformed slot is rejected rather than skipped: dropping one would schedule a volunteer outside
 * the hours they offered.
 */
private fun DocumentSnapshot.requireAvailability(): List<AvailabilitySlot> {
  val field =
      get(MembershipRequestSchema.AVAILABILITY) ?: invalid(MembershipRequestSchema.AVAILABILITY)
  val entries = field as? List<*> ?: invalid(MembershipRequestSchema.AVAILABILITY)
  if (entries.isEmpty()) invalid(MembershipRequestSchema.AVAILABILITY)
  return entries.map { entry ->
    val slot = entry as? Map<*, *> ?: invalid(MembershipRequestSchema.AVAILABILITY)
    AvailabilitySlot(
        startAt = requireSlotInstant(slot, MembershipRequestSchema.SlotFields.START_AT),
        endAt = requireSlotInstant(slot, MembershipRequestSchema.SlotFields.END_AT),
    )
  }
}

/** Both ends of a slot are required: a slot with one end is not a stretch of time. */
private fun DocumentSnapshot.requireSlotInstant(slot: Map<*, *>, field: String): Instant =
    (slot[field] as? Timestamp)?.toInstant() ?: invalid(MembershipRequestSchema.AVAILABILITY)

private fun DocumentSnapshot.requireString(field: String): String =
    get(field) as? String ?: invalid(field)

private fun DocumentSnapshot.requireInstant(field: String): Instant =
    (get(field) as? Timestamp)?.toInstant() ?: invalid(field)

private fun DocumentSnapshot.invalid(field: String): Nothing =
    throw IllegalStateException(
        "Membership request document '$id' has a missing or invalid '$field' field"
    )

/** Firestore keeps microsecond precision, so a sub-microsecond [Instant] is truncated on write. */
private fun Instant.toFirestoreTimestamp(): Timestamp = Timestamp(epochSecond, nano)
