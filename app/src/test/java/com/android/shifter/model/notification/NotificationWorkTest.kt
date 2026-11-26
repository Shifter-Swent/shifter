// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationWorkTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(context)
  }

  @Test
  fun schedule_plansOneRunEvery15Minutes() {
    scheduleNotificationWork(context)

    val work = works().single()
    assertEquals(WorkInfo.State.ENQUEUED, work.state)
    assertEquals(TimeUnit.MINUTES.toMillis(15), work.periodicityInfo?.repeatIntervalMillis)
  }

  @Test
  fun schedule_atEachLaunch_keepsTheRunAlreadyPlanned() {
    scheduleNotificationWork(context)
    val planned = works().single().id

    scheduleNotificationWork(context)

    assertEquals(listOf(planned), works().map { it.id })
  }

  @Test
  fun run_signedOut_runsNothing() = runTest {
    var ran = false

    runNotificationWork(userId = null, run = { ran = true }, onFailure = { throw it })

    assertTrue(!ran)
  }

  @Test
  fun run_runsForTheSignedInUser() = runTest {
    val users = mutableListOf<String>()

    runNotificationWork(userId = "u1", run = { users += it }, onFailure = { throw it })

    assertEquals(listOf("u1"), users)
  }

  @Test
  fun run_whenItFails_reportsIt_withoutThrowing() = runTest {
    val failures = mutableListOf<Exception>()

    runNotificationWork(
        userId = "u1",
        run = { throw IOException("offline") },
        onFailure = { failures += it },
    )

    assertEquals(listOf("offline"), failures.map { it.message })
  }

  @Test
  fun run_cancelled_isCancelled_notReported() = runTest {
    val failures = mutableListOf<Exception>()

    assertThrows(CancellationException::class.java) {
      runBlocking {
        runNotificationWork(
            userId = "u1",
            run = { throw CancellationException("stopped") },
            onFailure = { failures += it },
        )
      }
    }
    assertEquals(emptyList<Exception>(), failures)
  }

  /** The works under the name the app has always used, so renaming it never plans a second one. */
  private fun works(): List<WorkInfo> =
      WorkManager.getInstance(context).getWorkInfosForUniqueWork("notifications").get()
}
