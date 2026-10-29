// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import com.google.firebase.FirebaseApp
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
   * store a document no client may write, such as a malformed one. Each value is a [String] or an
   * [Instant], stored as a string or a timestamp.
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

  private fun toValue(value: Any): JSONObject =
      when (value) {
        is String -> JSONObject().put("stringValue", value)
        is Instant -> JSONObject().put("timestampValue", value.toString())
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
