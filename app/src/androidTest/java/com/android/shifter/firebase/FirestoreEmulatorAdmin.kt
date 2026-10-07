// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.firebase

import com.google.firebase.FirebaseApp
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Test cleanup only: the emulator's owner token bypasses client rules on the local emulator. */
object FirestoreEmulatorAdmin {
  suspend fun deleteDocument(path: String) =
      withContext(Dispatchers.IO) {
        val projectId = checkNotNull(FirebaseApp.getInstance().options.projectId)
        val url =
            URL(
                "http://${FirestoreEmulator.HOST}:${FirestoreEmulator.PORT}" +
                    "/v1/projects/$projectId/databases/(default)/documents/$path"
            )
        val connection = url.openConnection() as HttpURLConnection
        try {
          connection.requestMethod = "DELETE"
          connection.setRequestProperty("Authorization", "Bearer owner")
          connection.connectTimeout = 5_000
          connection.readTimeout = 5_000
          val status = connection.responseCode
          check(status in 200..299 || status == 404) {
            "Emulator cleanup failed for $path: $status"
          }
        } finally {
          connection.disconnect()
        }
      }
}
