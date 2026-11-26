// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The [ShownItems] of [userId], kept in the phone's preferences so a closed app, run in the
 * background, still finds them: one file per user, one entry per identity, its items as JSON.
 *
 * Each change is on disk when it returns, as the background run's process may be killed right
 * after: call it off the main thread.
 */
class SharedPreferencesShownItems(context: Context, userId: String) : ShownItems {

  private val preferences = context.preferences(SHOWN_ITEMS_FILE, userId)

  /** The items stored under [identity], or none when they cannot be read, as after an update. */
  override fun items(identity: String): List<InboxNotification> {
    val written = preferences.getString(identity, null) ?: return emptyList()
    return try {
      Json.decodeFromString(STORED_ITEMS, written).map { it.toItem() }
    } catch (_: IllegalArgumentException) {
      // A SerializationException, a kind this version no longer knows, or an item the contract
      // now refuses: the notification then starts again at its new items.
      emptyList()
    }
  }

  override fun put(identity: String, items: List<InboxNotification>) {
    preferences.edit(commit = true) {
      putString(identity, Json.encodeToString(STORED_ITEMS, items.map { StoredItem.of(it) }))
    }
  }

  override fun retainOnly(identities: Set<String>) {
    val gone = preferences.all.keys - identities
    if (gone.isNotEmpty()) preferences.edit(commit = true) { gone.forEach(::remove) }
  }
}

/**
 * The [SeenStore] of [userId] for [kind], kept in the phone's preferences: one file per user, in
 * which each key is prefixed with its kind, since a key is only unique within its kind.
 *
 * Each change is on disk when it returns, like [SharedPreferencesShownItems]: call it off the main
 * thread.
 */
class SharedPreferencesSeenStore(
    context: Context,
    userId: String,
    private val kind: NotificationKind,
) : SeenStore {

  private val preferences = context.preferences(SEEN_FILE, userId)

  override fun wasShown(key: String) = preferences.contains(entry(key))

  override fun markShown(key: String) =
      preferences.edit(commit = true) { putBoolean(entry(key), true) }

  override fun forget(key: String) = preferences.edit(commit = true) { remove(entry(key)) }

  private fun entry(key: String) = "${kind.name}:$key"
}

private const val SHOWN_ITEMS_FILE = "notification_shown_items"
private const val SEEN_FILE = "notification_seen"

/**
 * The preferences [file] of [userId]: each user has their own, so accounts never mix. A Firebase
 * uid holds letters and digits only, so it is safe in a file name.
 */
private fun Context.preferences(file: String, userId: String): SharedPreferences =
    getSharedPreferences("${file}_$userId", Context.MODE_PRIVATE)

private val STORED_ITEMS = ListSerializer(StoredItem.serializer())

/**
 * An [InboxNotification] as stored on the phone. Apart from it, so the inbox's contract stays free
 * of serialization; [createdAt] in epoch milliseconds, which drops the microseconds of Firestore:
 * the engine tells items apart by their id, and only sorts them by date.
 */
@Serializable
private data class StoredItem(
    val id: String,
    val kind: String,
    val eventId: String,
    val eventTitle: String,
    val senderId: String,
    val data: Map<String, String>,
    val createdAt: Long,
) {
  fun toItem() =
      InboxNotification(
          id = id,
          kind = NotificationKind.valueOf(kind),
          eventId = eventId,
          eventTitle = eventTitle,
          senderId = senderId,
          data = data,
          createdAt = Instant.ofEpochMilli(createdAt),
      )

  companion object {
    fun of(item: InboxNotification) =
        StoredItem(
            id = item.id,
            kind = item.kind.name,
            eventId = item.eventId,
            eventTitle = item.eventTitle,
            senderId = item.senderId,
            data = item.data,
            createdAt = item.createdAt.toEpochMilli(),
        )
  }
}
