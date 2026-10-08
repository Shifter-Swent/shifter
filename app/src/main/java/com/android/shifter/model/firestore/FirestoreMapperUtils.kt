// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

/*
 * Field-level primitives shared by the Firestore mappers, so that each entity's mapper holds only
 * the logic specific to that entity.
 *
 * Every reader takes the name of the entity being mapped and reports a malformed document as
 * "<Entity> document '<id>' has a missing or invalid '<field>' field", which keeps the failure
 * readable about the entity the caller asked for without every mapper repeating the same extraction
 * and message code.
 *
 * Fields are read through DocumentSnapshot.get rather than through the typed getString and
 * getTimestamp accessors, which raise a bare Firebase RuntimeException on a type mismatch instead of
 * the IllegalStateException a malformed document has to raise.
 */

/** Reports [field] of this document as missing or malformed, for the entity named [entity]. */
internal fun DocumentSnapshot.invalidField(entity: String, field: String): Nothing =
    throw IllegalStateException("$entity document '$id' has a missing or invalid '$field' field")

/** Reads a required string, failing when it is absent or of another type. */
internal fun DocumentSnapshot.requireString(entity: String, field: String): String =
    get(field) as? String ?: invalidField(entity, field)

/** Reads a required instant, failing when it is absent or not a Firestore timestamp. */
internal fun DocumentSnapshot.requireInstant(entity: String, field: String): Instant =
    (get(field) as? Timestamp)?.toInstant() ?: invalidField(entity, field)

/**
 * Reads an optional string: an absent or null field means the entity has no value for it, so both
 * map to null. A present value of another type is rejected like a wrongly typed required field,
 * because mapping it to null would turn a schema problem into "there is nothing here".
 */
internal fun DocumentSnapshot.optionalString(entity: String, field: String): String? =
    when (val value = get(field)) {
      null -> null
      is String -> value
      else -> invalidField(entity, field)
    }

/**
 * Reads a list of ids: an absent field means the list is empty. An explicit null is rejected
 * instead, since no mapper writes one, and so is a malformed entry rather than skipped: these lists
 * hold user ids, and dropping one would quietly hide the entity from that user.
 */
internal fun DocumentSnapshot.requireStringList(entity: String, field: String): List<String> {
  if (!contains(field)) return emptyList()
  val entries = get(field) as? List<*> ?: invalidField(entity, field)
  return entries.map { it as? String ?: invalidField(entity, field) }
}

/**
 * Reads a required whole number. Firestore stores whole numbers as Longs and fractional ones as
 * Doubles, both wider than [Int], so the value is read as a [Number] and narrowed here. A value the
 * narrowing would change is rejected rather than silently repaired: truncating `3.5`, or wrapping a
 * count beyond [Int.MAX_VALUE] round to a negative one, would show a number nobody wrote.
 */
internal fun DocumentSnapshot.requireInt(entity: String, field: String): Int {
  val value = get(field) as? Number ?: invalidField(entity, field)
  val whole = value.toLong()
  // Rejects a fractional, infinite or NaN value, then one that does not fit in an Int.
  if (whole.toDouble() != value.toDouble() || whole != whole.toInt().toLong())
      invalidField(entity, field)
  return whole.toInt()
}

/** Firestore keeps microsecond precision, so a sub-microsecond [Instant] is truncated on write. */
internal fun Instant.toFirestoreTimestamp(): Timestamp = Timestamp(epochSecond, nano)
