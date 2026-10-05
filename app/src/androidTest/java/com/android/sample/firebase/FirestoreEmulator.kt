// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import com.google.firebase.firestore.FirebaseFirestore

/**
 * Single source of truth for the Firestore emulator used by instrumented tests.
 *
 * The emulator runs on the *host* machine (started by `firebase emulators:exec`), while the tests
 * run inside the Android emulator. The Android emulator cannot reach the host through `localhost`:
 * there, `127.0.0.1` is the virtual device itself. `10.0.2.2` is the alias the Android emulator
 * exposes for the host's loopback interface.
 *
 * Instrumented tests must go through [firestore] and never through
 * `FirebaseFirestore.getInstance()` directly, so that they never talk to the production backend.
 */
object FirestoreEmulator {

  /** The host's loopback interface, as seen from inside the Android emulator. */
  const val HOST = "10.0.2.2"

  /** Must stay in sync with `emulators.firestore.port` in `firebase.json`. */
  const val PORT = 8080

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
}
