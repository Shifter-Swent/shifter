// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Based on Bootcamp authentication material.

package com.swent.shifter.model.authentication

import android.os.Bundle
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.GoogleAuthProvider

/**
 * Converts Credential Manager data into credentials that Firebase can authenticate.
 *
 * Keeping this SDK conversion behind an interface lets repository tests provide a fake helper.
 */
interface GoogleSignInHelper {
  /** Reads the Google ID-token credential from Credential Manager's response data. */
  fun extractIdTokenCredential(bundle: Bundle): GoogleIdTokenCredential

  /** Creates the Firebase credential used to authenticate the Google ID token. */
  fun toFirebaseCredential(idToken: String): AuthCredential
}

/** Production Google credential converter. */
class DefaultGoogleSignInHelper : GoogleSignInHelper {
  /** Converts the response bundle into a Google ID-token credential. */
  override fun extractIdTokenCredential(bundle: Bundle) = GoogleIdTokenCredential.createFrom(bundle)

  /** Wraps the Google ID token in a Firebase authentication credential. */
  override fun toFirebaseCredential(idToken: String) =
      GoogleAuthProvider.getCredential(idToken, null)
}
