// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Points Firebase at the local emulators (see `firebase.json`). Start them with `firebase
 * emulators:start` before running instrumented tests. `10.0.2.2` is the host seen from the Android
 * emulator.
 */
object FirebaseEmulator {
  private const val HOST = "10.0.2.2"
  private const val AUTH_PORT = 9099
  private const val FIRESTORE_PORT = 8080

  // useEmulator() may only be called once per process, before any other use of the instance.
  private val connected: Unit by lazy {
    FirebaseAuth.getInstance().useEmulator(HOST, AUTH_PORT)
    FirebaseFirestore.getInstance().useEmulator(HOST, FIRESTORE_PORT)
  }

  val auth: FirebaseAuth
    get() = connected.let { FirebaseAuth.getInstance() }

  val firestore: FirebaseFirestore
    get() = connected.let { FirebaseFirestore.getInstance() }
}
