// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the contract the fake shares with the Android provider, so the ViewModels built against it
 * see the same positions as on a device.
 */
class FakeLocationProviderTest {

  @Test
  fun positions_whenGranted_emitsTheUpdates() = runTest {
    val provider = FakeLocationProvider(LocationStatus.GRANTED)
    val received = mutableListOf<GeoPosition>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().toList(received) }

    provider.emit(LAUSANNE)
    provider.emit(GENEVA)

    assertEquals(listOf(LAUSANNE, GENEVA), received)
    job.cancel()
  }

  @Test
  fun positions_whenDenied_emitsNothingAndDoesNotListen() = runTest {
    val provider = FakeLocationProvider(LocationStatus.DENIED)
    val received = mutableListOf<GeoPosition>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().toList(received) }

    provider.emit(LAUSANNE)

    assertTrue(received.isEmpty())
    assertEquals(0, provider.activeCollectors)
    job.cancel()
  }

  @Test
  fun positions_whenGrantedLater_startsTheUpdates() = runTest {
    val provider = FakeLocationProvider(LocationStatus.DENIED)
    val received = mutableListOf<GeoPosition>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().toList(received) }

    provider.setStatus(LocationStatus.GRANTED)
    provider.emit(LAUSANNE)

    assertEquals(listOf(LAUSANNE), received)
    job.cancel()
  }

  @Test
  fun positions_whenLocationTurnedOff_stopsTheUpdates() = runTest {
    val provider = FakeLocationProvider(LocationStatus.GRANTED)
    val received = mutableListOf<GeoPosition>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().toList(received) }
    provider.emit(LAUSANNE)

    provider.setStatus(LocationStatus.LOCATION_OFF)
    provider.emit(GENEVA)

    assertEquals(listOf(LAUSANNE), received)
    assertEquals(0, provider.activeCollectors)
    job.cancel()
  }

  @Test
  fun positions_afterCancel_stopsListening() = runTest {
    val provider = FakeLocationProvider(LocationStatus.GRANTED)
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().collect {} }
    assertEquals(1, provider.activeCollectors)

    job.cancel()

    assertEquals(0, provider.activeCollectors)
  }

  @Test
  fun positions_whenCollected_readsTheStatusAgain() = runTest {
    val provider = FakeLocationProvider(LocationStatus.DENIED)
    provider.deviceStatus = LocationStatus.GRANTED
    val received = mutableListOf<GeoPosition>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) { provider.positions().toList(received) }

    provider.emit(LAUSANNE)

    assertEquals(listOf(LAUSANNE), received)
    job.cancel()
  }

  @Test
  fun refreshStatus_readsTheDeviceStatus() {
    val provider = FakeLocationProvider(LocationStatus.GRANTED)
    provider.deviceStatus = LocationStatus.LOCATION_OFF
    assertEquals(LocationStatus.GRANTED, provider.status.value)

    provider.refreshStatus()

    assertEquals(LocationStatus.LOCATION_OFF, provider.status.value)
    assertEquals(1, provider.refreshCount)
  }

  private companion object {
    val LAUSANNE = GeoPosition(46.5197, 6.6323)
    val GENEVA = GeoPosition(46.2044, 6.1432)
  }
}
