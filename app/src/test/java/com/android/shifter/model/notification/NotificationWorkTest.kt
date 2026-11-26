// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
  fun result_signedOut_succeedsWithoutRunning() = runTest {
    var ran = false

    val result = notificationWorkResult(userId = null) { ran = true }

    assertEquals(ListenableWorker.Result.success(), result)
    assertTrue(!ran)
  }

  @Test
  fun result_runsForTheSignedInUser_andSucceeds() = runTest {
    val users = mutableListOf<String>()

    val result = notificationWorkResult(userId = "u1") { users += it }

    assertEquals(ListenableWorker.Result.success(), result)
    assertEquals(listOf("u1"), users)
  }

  @Test
  fun result_whenTheRunFails_retries() = runTest {
    val result = notificationWorkResult(userId = "u1") { throw IOException("offline") }

    assertEquals(ListenableWorker.Result.retry(), result)
  }

  /** The works under the name the app has always used, so renaming it never plans a second one. */
  private fun works(): List<WorkInfo> =
      WorkManager.getInstance(context).getWorkInfosForUniqueWork("notifications").get()
}
