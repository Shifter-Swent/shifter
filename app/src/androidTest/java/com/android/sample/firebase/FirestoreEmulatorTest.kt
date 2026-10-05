// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Source
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Infrastructure smoke test: proves that an instrumented test can read and write through the
 * Firestore emulator. It fails if the emulator is not running, which is exactly what we want: no
 * test is allowed to silently fall back to the production backend.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreEmulatorTest {

  @Test
  fun writesAndReadsBackADocumentFromTheEmulator() {
    val document = FirestoreEmulator.firestore.collection(COLLECTION).document()

    try {
      await(document.set(mapOf(FIELD to VALUE)))
      // Source.SERVER bypasses the local cache, so the read really goes through the emulator.
      val snapshot = await(document.get(Source.SERVER))

      assertEquals(VALUE, snapshot.getString(FIELD))
    } finally {
      await(document.delete())
    }
  }

  private fun <T> await(task: Task<T>): T = Tasks.await(task, TIMEOUT_SECONDS, TimeUnit.SECONDS)

  private companion object {
    const val COLLECTION = "emulator-smoke-test"
    const val FIELD = "value"
    const val VALUE = "ok"
    const val TIMEOUT_SECONDS = 15L
  }
}
