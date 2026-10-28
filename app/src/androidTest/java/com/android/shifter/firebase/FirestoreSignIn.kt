// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

/**
 * Runs [signIn] and returns its result once Firestore has switched to the signed-in user.
 *
 * Firestore learns about a new user asynchronously, and it queues each write under the user it
 * believes is signed in. A write made right after a sign-in can therefore be queued under the
 * previous user and is only sent once that user signs in again: on the emulator, it hangs until the
 * test times out. Firebase Auth notifies Firestore before the public id token listeners, so once
 * such a listener sees the new user, any later Firestore call is ordered after the switch. That
 * order is not documented: `FirestoreSignInTest` would catch a change in it on an SDK upgrade.
 */
suspend fun <T> FirebaseAuth.signInForFirestore(signIn: suspend () -> T): T =
    step("sign-in") {
      val notified = MutableStateFlow<List<String?>>(emptyList())
      val listener = FirebaseAuth.IdTokenListener { auth ->
        notified.update { it + auth.currentUser?.uid }
      }
      addIdTokenListener(listener)
      try {
        // The first call reports the state at registration: it must not be taken for the sign-in's.
        val registration = notified.first { it.isNotEmpty() }
        val before = registration.size
        val result = signIn()
        val uid = checkNotNull(currentUser) { "signing in left no user signed in" }.uid
        // Signing in again as the same user changes nothing for Firestore: no notification to wait
        // for.
        if (uid != registration.last()) notified.first { uid in it.drop(before) }
        result
      } finally {
        removeIdTokenListener(listener)
      }
    }
