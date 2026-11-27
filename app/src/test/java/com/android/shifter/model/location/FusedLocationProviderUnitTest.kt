// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import android.Manifest
import android.app.Application
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Checks [FusedLocationProvider] without Google Play services: the fused client is mocked, and
 * Robolectric gives the permission and the location services state.
 */
@RunWith(RobolectricTestRunner::class)
class FusedLocationProviderUnitTest {

  private val application = ApplicationProvider.getApplicationContext<Application>()
  private val locationManager = application.getSystemService(LocationManager::class.java)
  private val client = mockk<FusedLocationProviderClient>(relaxed = true)
  private val callback = slot<LocationCallback>()

  @Before
  fun mockFusedClient() {
    mockkStatic(LocationServices::class)
    every { LocationServices.getFusedLocationProviderClient(any<Application>()) } returns client
    every {
      client.requestLocationUpdates(any<LocationRequest>(), capture(callback), any<Looper>())
    } returns Tasks.forResult(null)
    shadowOf(locationManager).setLocationEnabled(true)
  }

  @After
  fun unmockFusedClient() {
    unmockkStatic(LocationServices::class)
  }

  @Test
  fun status_withoutPermission_isDenied() {
    val provider = FusedLocationProvider(application)

    assertEquals(LocationStatus.DENIED, provider.status.value)
  }

  @Test
  fun status_withOnlyCoarsePermission_isDenied() {
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

    val provider = FusedLocationProvider(application)

    assertEquals(LocationStatus.DENIED, provider.status.value)
  }

  @Test
  fun status_withLocationTurnedOff_isLocationOff() {
    grantFinePermission()
    shadowOf(locationManager).setLocationEnabled(false)

    val provider = FusedLocationProvider(application)

    assertEquals(LocationStatus.LOCATION_OFF, provider.status.value)
  }

  @Test
  fun status_withPermissionAndLocationOn_isGranted() {
    grantFinePermission()

    val provider = FusedLocationProvider(application)

    assertEquals(LocationStatus.GRANTED, provider.status.value)
  }

  @Test
  fun refreshStatus_afterPermissionGranted_isGranted() {
    val provider = FusedLocationProvider(application)
    grantFinePermission()

    provider.refreshStatus()

    assertEquals(LocationStatus.GRANTED, provider.status.value)
  }

  @Test
  fun positions_whenGranted_emitsTheLocationResultsWithTheirAccuracy() = runTest {
    grantFinePermission()
    val provider = FusedLocationProvider(application, intervalMillis = 1_000L)
    val received = mutableListOf<MeasuredPosition>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().toList(received) }

    callback.captured.onLocationResult(LocationResult.create(listOf(location(LAUSANNE, 8f))))
    callback.captured.onLocationResult(LocationResult.create(emptyList()))
    callback.captured.onLocationResult(LocationResult.create(listOf(location(GENEVA, 120f))))

    assertEquals(
        listOf(MeasuredPosition(LAUSANNE, 8.0), MeasuredPosition(GENEVA, 120.0)),
        received,
    )
    job.cancel()
  }

  @Test
  fun positions_withoutAccuracy_givesAnInfiniteOne() = runTest {
    grantFinePermission()
    val provider = FusedLocationProvider(application)
    val received = mutableListOf<MeasuredPosition>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().toList(received) }

    callback.captured.onLocationResult(LocationResult.create(listOf(location(LAUSANNE, null))))

    assertEquals(listOf(MeasuredPosition(LAUSANNE, Double.POSITIVE_INFINITY)), received)
    job.cancel()
  }

  @Test
  fun positions_whenDenied_doesNotRequestUpdates() = runTest {
    val provider = FusedLocationProvider(application)
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }

    verify(exactly = 0) {
      client.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>())
    }
    job.cancel()
  }

  @Test
  fun positions_whenCollected_readsTheStatusAgain() = runTest {
    val provider = FusedLocationProvider(application)
    grantFinePermission()
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }

    assertEquals(LocationStatus.GRANTED, provider.status.value)
    assertTrue(callback.isCaptured)
    job.cancel()
  }

  @Test
  fun positions_afterCancel_removesTheUpdates() = runTest {
    grantFinePermission()
    val provider = FusedLocationProvider(application)
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }

    job.cancel()

    verify { client.removeLocationUpdates(callback.captured) }
  }

  @Test
  fun positions_whenLocationTurnedOff_stopsTheUpdates() = runTest {
    grantFinePermission()
    val provider = FusedLocationProvider(application)
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }

    setLocationEnabled(false)

    assertEquals(LocationStatus.LOCATION_OFF, provider.status.value)
    verify { client.removeLocationUpdates(callback.captured) }
    job.cancel()
  }

  @Test
  fun positions_afterLocationTurnedBackOn_resumesTheUpdates() = runTest {
    // The quick settings do not pause the activity, so only the mode broadcast notices this.
    grantFinePermission()
    val provider = FusedLocationProvider(application)
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }
    setLocationEnabled(false)

    setLocationEnabled(true)

    assertEquals(LocationStatus.GRANTED, provider.status.value)
    verify(exactly = 2) {
      client.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>())
    }
    job.cancel()
  }

  @Test
  fun positions_afterRefreshWithLocationBackOn_resumesTheUpdates() = runTest {
    grantFinePermission()
    shadowOf(locationManager).setLocationEnabled(false)
    val provider = FusedLocationProvider(application)
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }
    assertEquals(LocationStatus.LOCATION_OFF, provider.status.value)
    shadowOf(locationManager).setLocationEnabled(true)

    provider.refreshStatus()

    assertEquals(LocationStatus.GRANTED, provider.status.value)
    verify(exactly = 1) {
      client.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>())
    }
    job.cancel()
  }

  @Test
  fun positions_withoutAFix_keepsTheUpdates() = runTest {
    // Indoors or while the GPS warms up the location is unavailable, yet location services are on.
    grantFinePermission()
    val provider = FusedLocationProvider(application)
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }

    callback.captured.onLocationAvailability(availability(isAvailable = false))

    assertEquals(LocationStatus.GRANTED, provider.status.value)
    verify(exactly = 0) { client.removeLocationUpdates(any<LocationCallback>()) }
    job.cancel()
  }

  @Test
  fun positions_afterCancel_stopsFollowingTheLocationMode() = runTest {
    grantFinePermission()
    val provider = FusedLocationProvider(application)
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }

    job.cancel()
    setLocationEnabled(false)

    assertEquals(LocationStatus.GRANTED, provider.status.value)
  }

  @Test
  fun positions_withASlowCollector_keepsTheLatestPosition() = runTest {
    grantFinePermission()
    val provider = FusedLocationProvider(application)
    val received = mutableListOf<GeoPosition>()
    val job = launch {
      provider.positions().collect {
        received += it.position
        delay(10_000L)
      }
    }
    runCurrent()

    listOf(LAUSANNE, GENEVA, BERN, ZURICH).forEach {
      callback.captured.onLocationResult(LocationResult.create(listOf(location(it, 5f))))
      runCurrent()
    }
    advanceUntilIdle()

    assertEquals(listOf(LAUSANNE, ZURICH), received)
    job.cancel()
  }

  @Test
  fun positions_afterAFailedRequest_requestsAgainAtTheNextInterval() = runTest {
    grantFinePermission()
    every {
      client.requestLocationUpdates(any<LocationRequest>(), capture(callback), any<Looper>())
    } returnsMany listOf(failedTask(), Tasks.forResult(null))
    val provider = FusedLocationProvider(application, intervalMillis = 1_000L)
    val received = mutableListOf<MeasuredPosition>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().toList(received) }

    advanceTimeBy(1_000L)
    runCurrent()
    callback.captured.onLocationResult(LocationResult.create(listOf(location(LAUSANNE, 8f))))

    verify(exactly = 2) {
      client.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>())
    }
    assertEquals(listOf(MeasuredPosition(LAUSANNE, 8.0)), received)
    job.cancel()
  }

  @Test
  fun positions_whenLocationTurnedOffAfterAFailedRequest_stopsRequesting() = runTest {
    grantFinePermission()
    every {
      client.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>())
    } returns failedTask()
    val provider = FusedLocationProvider(application, intervalMillis = 1_000L)
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }

    setLocationEnabled(false)
    advanceTimeBy(5_000L)

    verify(exactly = 1) {
      client.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>())
    }
    job.cancel()
  }

  /** A request rejected at once: the task calls its failure listener as soon as it is added. */
  private fun failedTask(): Task<Void> {
    val task = mockk<Task<Void>>()
    every { task.addOnFailureListener(any<OnFailureListener>()) } answers
        {
          firstArg<OnFailureListener>().onFailure(SecurityException("Location request rejected"))
          task
        }
    return task
  }

  /** Turns location services on or off and delivers the broadcast Android sends for it. */
  private fun setLocationEnabled(enabled: Boolean) {
    shadowOf(locationManager).setLocationEnabled(enabled)
    application.sendBroadcast(Intent(LocationManager.MODE_CHANGED_ACTION))
    shadowOf(Looper.getMainLooper()).idle()
  }

  private fun grantFinePermission() {
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
  }

  /** A location at [position], with no accuracy when [accuracyMeters] is null. */
  private fun location(position: GeoPosition, accuracyMeters: Float?) =
      Location(LocationManager.GPS_PROVIDER).apply {
        latitude = position.latitude
        longitude = position.longitude
        accuracyMeters?.let { accuracy = it }
      }

  private fun availability(isAvailable: Boolean) =
      mockk<LocationAvailability> { every { isLocationAvailable } returns isAvailable }

  private companion object {
    val LAUSANNE = GeoPosition(46.5197, 6.6323)
    val GENEVA = GeoPosition(46.2044, 6.1432)
    val BERN = GeoPosition(46.9480, 7.4474)
    val ZURICH = GeoPosition(47.3769, 8.5417)
  }
}
