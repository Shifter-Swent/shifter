// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Test setup and cleanup only: the emulator's owner token bypasses client rules on the local
 * emulator.
 */
object FirestoreEmulatorAdmin {
  suspend fun deleteDocument(path: String) =
      request("DELETE", path) { status ->
        check(status in 200..299 || status == 404) { "Emulator cleanup failed for $path: $status" }
      }

  /**
   * Creates the document at [path] with [fields], whatever `firestore.rules` say, so a test can
   * store a document no client may write, such as a malformed one. Each value is null, a [String],
   * a [Boolean], an [Int] or a [Long], a [Double], an [Instant] or a [Timestamp], a nested [Map] or
   * a [List] of them, stored as null, a string, a boolean, an integer, a number, a timestamp, a map
   * or an array.
   */
  suspend fun createDocument(path: String, fields: Map<String, Any?>) {
    val parent = path.substringBeforeLast('/')
    val id = path.substringAfterLast('/')
    val body =
        JSONObject()
            .put(
                "fields",
                JSONObject().apply {
                  fields.forEach { (name, value) -> put(name, toValue(value)) }
                },
            )
            .toString()
    request("POST", "$parent?documentId=$id", body) { status ->
      check(status in 200..299) { "Emulator setup failed for $path: $status" }
    }
  }

  /**
   * Makes [userIds] the participants of the event [eventId], which only the backend writes.
   *
   * @return the path of the created document, for the test to clean up.
   */
  suspend fun createParticipants(eventId: String, userIds: List<String>): String {
    val path = "eventParticipants/$eventId"
    createDocument(path, mapOf("participantIds" to userIds))
    return path
  }

  private fun toValue(value: Any?): JSONObject =
      when (value) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", value)
        is Boolean -> JSONObject().put("booleanValue", value)
        // The REST API takes 64-bit integers as strings.
        is Int,
        is Long -> JSONObject().put("integerValue", value.toString())
        // JSON has no NaN nor infinity, which the REST API takes as strings instead.
        is Double ->
            JSONObject()
                .put(
                    "doubleValue",
                    when {
                      value.isFinite() -> value
                      value.isNaN() -> "NaN"
                      value > 0 -> "Infinity"
                      else -> "-Infinity"
                    },
                )
        is Instant -> JSONObject().put("timestampValue", value.toString())
        is Timestamp ->
            JSONObject()
                .put(
                    "timestampValue",
                    Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong()).toString(),
                )
        is Map<*, *> ->
            JSONObject()
                .put(
                    "mapValue",
                    JSONObject()
                        .put(
                            "fields",
                            JSONObject().apply {
                              value.forEach { (name, nested) ->
                                put(name as String, toValue(nested))
                              }
                            },
                        ),
                )
        is List<*> ->
            JSONObject()
                .put(
                    "arrayValue",
                    JSONObject().put("values", JSONArray(value.map { toValue(it) })),
                )
        else -> throw IllegalArgumentException("Unsupported field value: $value")
      }

  private suspend fun request(
      method: String,
      path: String,
      body: String? = null,
      checkStatus: (Int) -> Unit,
  ) =
      withContext(Dispatchers.IO) {
        val projectId = checkNotNull(FirebaseApp.getInstance().options.projectId)
        val url =
            URL(
                "http://${FirestoreEmulator.HOST}:${FirestoreEmulator.PORT}" +
                    "/v1/projects/$projectId/databases/(default)/documents/$path"
            )
        val connection = url.openConnection() as HttpURLConnection
        try {
          connection.requestMethod = method
          connection.setRequestProperty("Authorization", "Bearer owner")
          connection.connectTimeout = 5_000
          connection.readTimeout = 5_000
          if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray()) }
          }
          checkStatus(connection.responseCode)
        } finally {
          connection.disconnect()
        }
      }
}
