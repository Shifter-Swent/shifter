// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import android.Manifest
import android.app.Application
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
  fun positions_whenGranted_emitsTheLocationResults() = runTest {
    grantFinePermission()
    val provider = FusedLocationProvider(application, intervalMillis = 1_000L)
    val received = mutableListOf<LatLng>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().toList(received) }

    callback.captured.onLocationResult(LocationResult.create(listOf(location(LAUSANNE))))
    callback.captured.onLocationResult(LocationResult.create(emptyList()))
    callback.captured.onLocationResult(LocationResult.create(listOf(location(GENEVA))))

    assertEquals(listOf(LAUSANNE, GENEVA), received)
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
  fun positions_whenLocationBecomesUnavailable_stopsTheUpdates() = runTest {
    grantFinePermission()
    val provider = FusedLocationProvider(application)
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }
    shadowOf(locationManager).setLocationEnabled(false)

    callback.captured.onLocationAvailability(availability(isAvailable = false))

    assertEquals(LocationStatus.LOCATION_OFF, provider.status.value)
    verify { client.removeLocationUpdates(callback.captured) }
    job.cancel()
  }

  @Test
  fun positions_whenLocationStaysAvailable_keepsTheStatus() = runTest {
    grantFinePermission()
    val provider = FusedLocationProvider(application)
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }
    shadowOf(locationManager).setLocationEnabled(false)

    callback.captured.onLocationAvailability(availability(isAvailable = true))

    assertEquals(LocationStatus.GRANTED, provider.status.value)
    job.cancel()
  }

  @Test
  fun positions_whenTheRequestFails_throwsTheError() = runTest {
    grantFinePermission()
    val failure = SecurityException("Location request rejected")
    val failedTask = mockk<Task<Void>>()
    every { failedTask.addOnFailureListener(any<OnFailureListener>()) } answers
        {
          firstArg<OnFailureListener>().onFailure(failure)
          failedTask
        }
    every {
      client.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>())
    } returns failedTask
    val provider = FusedLocationProvider(application)

    val error = runCatching { provider.positions().first() }.exceptionOrNull()

    assertTrue(error is SecurityException)
    assertEquals(failure.message, error?.message)
  }

  private fun grantFinePermission() {
    shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
  }

  private fun location(position: LatLng) =
      Location(LocationManager.GPS_PROVIDER).apply {
        latitude = position.latitude
        longitude = position.longitude
      }

  private fun availability(isAvailable: Boolean) =
      mockk<LocationAvailability> { every { isLocationAvailable } returns isAvailable }

  private companion object {
    val LAUSANNE = LatLng(46.5197, 6.6323)
    val GENEVA = LatLng(46.2044, 6.1432)
  }
}
