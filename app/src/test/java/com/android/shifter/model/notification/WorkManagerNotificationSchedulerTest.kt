// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Covers the contract of [NotificationScheduler.checkAt] with WorkManager. */
@RunWith(RobolectricTestRunner::class)
class WorkManagerNotificationSchedulerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var scheduler: WorkManagerNotificationScheduler

  @Before
  fun setUp() {
    // A run that does nothing in place of the engine's, which would read Firebase.
    val doNothing =
        object : WorkerFactory() {
          override fun createWorker(
              appContext: Context,
              workerClassName: String,
              workerParameters: WorkerParameters,
          ): ListenableWorker =
              object : Worker(appContext, workerParameters) {
                override fun doWork() = Result.success()
              }
        }
    WorkManagerTestInitHelper.initializeTestWorkManager(
        context,
        Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(doNothing)
            .build(),
    )
    scheduler = WorkManagerNotificationScheduler(context, Clock.fixed(NOW, ZoneOffset.UTC))
  }

  @Test
  fun checkAt_plansARunAtThatTime() {
    scheduler.checkAt(IN_10_MINUTES)

    val run = runsAt(IN_10_MINUTES).single()
    assertEquals(WorkInfo.State.ENQUEUED, run.state)
    assertEquals(Duration.ofMinutes(10).toMillis(), run.initialDelayMillis)
    // WorkManager tags each run with its worker's class: the factory above runs none of them.
    assertTrue(NotificationWorker::class.java.name in run.tags)
  }

  @Test
  fun checkAt_theSameTimeTwice_keepsTheRunPlanned() {
    scheduler.checkAt(IN_10_MINUTES)
    val planned = runsAt(IN_10_MINUTES).single().id

    scheduler.checkAt(IN_10_MINUTES)

    assertEquals(listOf(planned), runsAt(IN_10_MINUTES).map { it.id })
  }

  @Test
  fun checkAt_twoTimes_plansOneRunEach_theLaterNotDroppingTheEarlier() {
    scheduler.checkAt(IN_10_MINUTES)
    scheduler.checkAt(NOW + Duration.ofMinutes(40))

    assertEquals(
        Duration.ofMinutes(10).toMillis(),
        runsAt(IN_10_MINUTES).single().initialDelayMillis,
    )
    assertEquals(
        Duration.ofMinutes(40).toMillis(),
        runsAt(NOW + Duration.ofMinutes(40)).single().initialDelayMillis,
    )
  }

  @Test
  fun checkAt_aTimePassed_runsAtOnce() {
    val passed = NOW - Duration.ofMinutes(1)

    scheduler.checkAt(passed)

    assertEquals(0L, runsAt(passed).single().initialDelayMillis)
  }

  @Test
  fun checkAt_aTimeFinerThanAMillisecond_runsNoEarlierThanIt() {
    val time = IN_10_MINUTES.plusNanos(1)

    scheduler.checkAt(time)

    assertEquals(Duration.ofMinutes(10).toMillis() + 1, runsAt(time).single().initialDelayMillis)
  }

  @Test
  fun checkAt_theSameTimeOnceItsRunIsDone_plansAnotherRun() {
    scheduler.checkAt(IN_10_MINUTES)
    val done = runsAt(IN_10_MINUTES).single().id
    WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(done)
    assertEquals(WorkInfo.State.SUCCEEDED, runsAt(IN_10_MINUTES).single().state)

    scheduler.checkAt(IN_10_MINUTES)

    val again = runsAt(IN_10_MINUTES).single()
    assertNotEquals(done, again.id)
    assertEquals(WorkInfo.State.ENQUEUED, again.state)
  }

  /** The runs asked for [time], under the name the app has always given them. */
  private fun runsAt(time: Instant): List<WorkInfo> =
      WorkManager.getInstance(context)
          .getWorkInfosForUniqueWork("notifications-at-${time.toEpochMilli()}")
          .get()

  private companion object {
    val NOW: Instant = Instant.parse("2026-11-27T08:00:00Z")
    val IN_10_MINUTES: Instant = NOW + Duration.ofMinutes(10)
  }
}
