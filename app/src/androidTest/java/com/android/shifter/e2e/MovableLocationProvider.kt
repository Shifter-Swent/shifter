// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.e2e

import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.model.location.LocationProvider
import com.swent.shifter.model.location.LocationStatus
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Stands in for the device's position in the end-to-end tests: the permission is granted, and the
 * phone is wherever the test puts it with [position].
 *
 * Like the real provider, [positions] gives the current position at regular intervals, so a
 * collector that starts late, or a move made before it starts, is never missed.
 */
class MovableLocationProvider(start: GeoPosition) : LocationProvider {

  /** Where the phone is now; the next emission of [positions] reports it. */
  @Volatile var position: GeoPosition = start

  /** How many positions were given so far, to wait until the app has read some. */
  val emitted = AtomicInteger(0)

  override val status: StateFlow<LocationStatus> =
      MutableStateFlow(LocationStatus.GRANTED).asStateFlow()

  override fun refreshStatus() = Unit

  override fun positions(): Flow<GeoPosition> = flow {
    while (true) {
      emit(position)
      emitted.incrementAndGet()
      delay(INTERVAL_MILLIS)
    }
  }

  private companion object {
    const val INTERVAL_MILLIS = 500L
  }
}
