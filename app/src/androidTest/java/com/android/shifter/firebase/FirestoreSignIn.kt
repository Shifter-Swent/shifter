// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Runs [signIn] and returns its result once Firestore has switched to the signed-in user.
 *
 * Firestore learns about a new user asynchronously, and it queues each write under the user it
 * believes is signed in. A write made right after a sign-in can therefore be queued under the
 * previous user and is only sent once that user signs in again: on the emulator, it hangs until the
 * test times out. Firebase Auth notifies Firestore before the public id token listeners, so once
 * such a listener sees the new user, any later Firestore call is ordered after the switch.
 */
suspend fun <T> FirebaseAuth.signInForFirestore(signIn: suspend () -> T): T =
    step("sign-in") {
      val notifiedUid = MutableStateFlow<String?>(null)
      val listener = FirebaseAuth.IdTokenListener { notifiedUid.value = it.currentUser?.uid }
      addIdTokenListener(listener)
      try {
        val result = signIn()
        val uid = checkNotNull(currentUser) { "signing in left no user signed in" }.uid
        notifiedUid.first { it == uid }
        result
      } finally {
        removeIdTokenListener(listener)
      }
    }
