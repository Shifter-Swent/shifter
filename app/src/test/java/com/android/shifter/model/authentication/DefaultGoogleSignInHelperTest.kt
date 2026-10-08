// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.authentication

import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Unit tests for the production [DefaultGoogleSignInHelper], without any fake in between. */
@RunWith(RobolectricTestRunner::class)
class DefaultGoogleSignInHelperTest {

  private val helper = DefaultGoogleSignInHelper()

  @Test
  fun extractIdTokenCredential_readsTheIdTokenCredentialManagerReturned() {
    // The bundle Credential Manager hands back after the user picked a Google account.
    val returned = GoogleIdTokenCredential.Builder().setId(EMAIL).setIdToken(ID_TOKEN).build()

    val extracted = helper.extractIdTokenCredential(returned.data)

    assertEquals(ID_TOKEN, extracted.idToken)
    assertEquals(EMAIL, extracted.id)
  }

  @Test
  fun toFirebaseCredential_createsAGoogleProviderCredential() {
    val credential = helper.toFirebaseCredential(ID_TOKEN)

    assertEquals(GoogleAuthProvider.PROVIDER_ID, credential.provider)
  }

  private companion object {
    const val EMAIL = "volunteer@example.com"

    /**
     * An unsigned Google ID token: the library parses its payload, but never checks a signature.
     */
    val ID_TOKEN =
        jwt("""{"alg":"none"}""") + "." + jwt("""{"sub":"google-1","email":"$EMAIL"}""") + "."

    private fun jwt(json: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())
  }
}
