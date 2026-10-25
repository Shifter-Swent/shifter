// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Checks [FusedLocationProvider] on the emulator, with the permission granted, location services on
 * (the emulator default) and a mocked location instead of the GPS.
 */
@RunWith(AndroidJUnit4::class)
class FusedLocationProviderTest {

  @get:Rule
  val permissions: GrantPermissionRule =
      GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val client = LocationServices.getFusedLocationProviderClient(context)
  private val provider = FusedLocationProvider(context, intervalMillis = 1_000L)

  /** Lets the app under test set mock locations, then turns the mock mode on. */
  @Before
  fun enableMockMode() {
    InstrumentationRegistry.getInstrumentation()
        .uiAutomation
        .executeShellCommand("appops set ${context.packageName} android:mock_location allow")
        .close()
    runBlocking { client.setMockMode(true).await() }
  }

  @After
  fun disableMockMode() {
    runBlocking { client.setMockMode(false).await() }
  }

  @Test
  fun status_withPermissionGranted_isGranted() {
    provider.refreshStatus()

    assertEquals(LocationStatus.GRANTED, provider.status.value)
  }

  @Test
  fun positions_withMockLocation_emitsIt() {
    val position = runBlocking {
      withTimeout(TIMEOUT_MILLIS) {
        // The mock location is pushed again until the provider has started and delivers it.
        val pusher = launch {
          while (isActive) {
            client.setMockLocation(mockLocation(LAUSANNE)).await()
            delay(PUSH_INTERVAL_MILLIS)
          }
        }
        provider.positions().first().also { pusher.cancel() }
      }
    }

    assertEquals(LAUSANNE, position)
  }

  private fun mockLocation(position: LatLng) =
      Location(LocationManager.GPS_PROVIDER).apply {
        latitude = position.latitude
        longitude = position.longitude
        accuracy = 1f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
      }

  private companion object {
    val LAUSANNE = LatLng(46.5197, 6.6323)
    const val TIMEOUT_MILLIS = 30_000L
    const val PUSH_INTERVAL_MILLIS = 500L
  }
}
