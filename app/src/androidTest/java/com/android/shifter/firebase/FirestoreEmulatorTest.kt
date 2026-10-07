package com.android.shifter.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Source
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Infrastructure smoke test: proves that an instrumented test can sign in and read through the
 * Firebase emulators. It fails if the emulators are not running, which is exactly what we want: no
 * test is allowed to silently fall back to the production backend.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreEmulatorTest {

  @Test
  fun signsInAndReadsFromTheEmulator() {
    val uid = await(FirestoreEmulator.auth.signInAnonymously()).user!!.uid

    try {
      // `firestore.rules` only lets a user read their own profile. Source.SERVER bypasses the local
      // cache, so the read really goes through the emulator.
      val snapshot =
          await(FirestoreEmulator.firestore.collection(USERS).document(uid).get(Source.SERVER))

        Assert.assertFalse(snapshot.exists())
    } finally {
      FirestoreEmulator.auth.signOut()
    }
  }

  private fun <T> await(task: Task<T>): T = Tasks.await(task, TIMEOUT_SECONDS, TimeUnit.SECONDS)

  private companion object {
    const val USERS = "users"
    const val TIMEOUT_SECONDS = 15L
  }
}