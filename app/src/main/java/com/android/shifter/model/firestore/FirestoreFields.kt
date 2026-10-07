// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant

/*
 * Field readers and writers shared by the Firestore mappers of every model.
 *
 * Readers throw [IllegalStateException] when a field is missing or has an unexpected type: a
 * document that cannot be mapped is a schema problem, which must not be mistaken for "no value".
 * Every field is read through [DocumentSnapshot.get] rather than through the typed `getString` and
 * `getTimestamp` accessors, which raise a bare Firebase `RuntimeException` on a type mismatch and
 * would therefore break that contract.
 */

internal fun DocumentSnapshot.requireString(field: String): String =
    get(field) as? String ?: invalid(field)

internal fun DocumentSnapshot.requireInstant(field: String): Instant =
    (get(field) as? Timestamp)?.toInstant() ?: invalid(field)

/**
 * An absent or null field means the document has no value for it, which is why both map to null. A
 * present value of another type is rejected like a wrongly typed required field: mapping it to null
 * would turn a schema problem into "this document has nothing here".
 */
internal fun DocumentSnapshot.optionalString(field: String): String? =
    when (val value = get(field)) {
      null -> null
      is String -> value
      else -> invalid(field)
    }

/**
 * An absent field means an empty list. A malformed entry is rejected rather than skipped: the lists
 * read this way hold user ids, and dropping one would hide the document from that user.
 */
internal fun DocumentSnapshot.requireStringList(field: String): List<String> {
  val value = get(field) ?: return emptyList()
  val entries = value as? List<*> ?: invalid(field)
  return entries.map { it as? String ?: invalid(field) }
}

/** The failure every reader raises; the document path tells which collection it comes from. */
internal fun DocumentSnapshot.invalid(field: String): Nothing =
    throw IllegalStateException(
        "Document '${reference.path}' has a missing or invalid '$field' field"
    )

/** Firestore keeps microsecond precision, so a sub-microsecond [Instant] is truncated on write. */
internal fun Instant.toFirestoreTimestamp(): Timestamp = Timestamp(epochSecond, nano)
