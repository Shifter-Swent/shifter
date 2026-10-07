// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import android.util.Base64
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * Single source of truth for the Firebase Authentication emulator used by instrumented tests.
 *
 * Like [FirestoreEmulator], the emulator runs on the host and is reached through `10.0.2.2`. The
 * Auth SDK talks to it over plain HTTP, which the debug-only network security config allows.
 *
 * Instrumented tests must go through [auth] and never through `FirebaseAuth.getInstance()`
 * directly, so that they never create accounts on the production backend.
 */
object AuthEmulator {

  /** The host's loopback interface, as seen from inside the Android emulator. */
  const val HOST = FirestoreEmulator.HOST

  /** Must stay in sync with `emulators.auth.port` in `firebase.json`. */
  const val PORT = 9099

  /**
   * The FirebaseAuth instance every instrumented test should use, wired to the emulator.
   *
   * `useEmulator` has to be called before Auth is used, so the instance is created lazily and
   * shared by the whole instrumentation run.
   */
  val auth: FirebaseAuth by
      lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FirebaseAuth.getInstance().apply { useEmulator(HOST, PORT) }
      }

  /** Deletes every account stored in the emulator, through its REST admin endpoint. */
  fun clearAccounts() {
    val projectId = checkNotNull(FirebaseApp.getInstance().options.projectId)
    val url = URL("http://$HOST:$PORT/emulator/v1/projects/$projectId/accounts")
    val connection = url.openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "DELETE"
      val code = connection.responseCode
      check(code == HttpURLConnection.HTTP_OK) { "clearing emulator accounts failed: HTTP $code" }
    } finally {
      connection.disconnect()
    }
  }

  /**
   * Builds the unsigned Google ID token of a fake Google account.
   *
   * The emulator does not verify signatures, so any JWT whose payload carries a `sub` is accepted
   * as a Google identity: the same [subject] always maps to the same Firebase user.
   */
  fun fakeGoogleIdToken(subject: String, email: String, name: String): String {
    val header = JSONObject().put("alg", "none").put("typ", "JWT")
    val payload =
        JSONObject()
            .put("sub", subject)
            .put("email", email)
            .put("email_verified", true)
            .put("name", name)
    return "${base64Url(header)}.${base64Url(payload)}."
  }

  private fun base64Url(json: JSONObject): String =
      Base64.encodeToString(
          json.toString().toByteArray(),
          Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
      )
}
