// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import com.google.firebase.FirebaseApp
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
   * Whether the document at [path] exists, whatever `firestore.rules` say, so a test can check that
   * a document nobody may read any more is really gone.
   */
  suspend fun documentExists(path: String): Boolean {
    var status = 0
    request("GET", path) { status = it }
    check(status == 200 || status == 404) { "Emulator read failed for $path: $status" }
    return status == 200
  }

  /**
   * Creates the document at [path] with [fields], whatever `firestore.rules` say, so a test can
   * store a document no client may write, such as a malformed one. Each value is a [String], a
   * [Boolean], a [Double], an [Instant], a nested [Map] or a [List] of them, stored as a string, a
   * boolean, a number, a timestamp, a map or an array.
   */
  suspend fun createDocument(path: String, fields: Map<String, Any>) {
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

  private fun toValue(value: Any): JSONObject =
      when (value) {
        is String -> JSONObject().put("stringValue", value)
        is Boolean -> JSONObject().put("booleanValue", value)
        is Double -> JSONObject().put("doubleValue", value)
        is Instant -> JSONObject().put("timestampValue", value.toString())
        is Map<*, *> ->
            JSONObject()
                .put(
                    "mapValue",
                    JSONObject()
                        .put(
                            "fields",
                            JSONObject().apply {
                              value.forEach { (name, nested) ->
                                put(name as String, toValue(checkNotNull(nested)))
                              }
                            },
                        ),
                )
        is List<*> ->
            JSONObject()
                .put(
                    "arrayValue",
                    JSONObject().put("values", JSONArray(value.map { toValue(checkNotNull(it)) })),
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
