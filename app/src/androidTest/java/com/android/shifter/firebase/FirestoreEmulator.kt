package com.swent.shifter.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Single source of truth for the Firebase emulators (Firestore and Auth) used by instrumented
 * tests. The Firestore emulator enforces `firestore.rules`, so tests sign in through [auth].
 *
 * The emulator runs on the *host* machine (started by `firebase emulators:exec`), while the tests
 * run inside the Android emulator. The Android emulator cannot reach the host through `localhost`:
 * there, `127.0.0.1` is the virtual device itself. `10.0.2.2` is the alias the Android emulator
 * exposes for the host's loopback interface.
 *
 * Instrumented tests must go through [firestore] and [auth] and never through `getInstance()`
 * directly, so that they never talk to the production backend.
 */
object FirestoreEmulator {

  /** The host's loopback interface, as seen from inside the Android emulator. */
  const val HOST = "10.0.2.2"

  /** Must stay in sync with `emulators.firestore.port` in `firebase.json`. */
  const val PORT = 8080

  /** Must stay in sync with `emulators.auth.port` in `firebase.json`. */
  const val AUTH_PORT = 9099

  /**
   * The Firestore instance every instrumented test should use, wired to the emulator.
   *
   * `useEmulator` has to be called before Firestore is used and may only be called once per
   * process, so the instance is created lazily and shared by the whole instrumentation run.
   */
  val firestore: FirebaseFirestore by
      lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FirebaseFirestore.getInstance().apply { useEmulator(HOST, PORT) }
      }

  /** The FirebaseAuth instance every instrumented test should use, wired to the emulator. */
  val auth: FirebaseAuth by
      lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        FirebaseAuth.getInstance().apply { useEmulator(HOST, AUTH_PORT) }
      }
}
