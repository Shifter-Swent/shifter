// Co-authored-by: Claude Opus 4.8 <noreply@anthropic.com>
package com.swent.shifter.model.location

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest

/**
 * In-memory [LocationProvider] for instrumented tests, so a test needs no GPS.
 *
 * The latest position set with [setPosition] is replayed to every collector of [positions], so a
 * publishing session that starts after the position is set still receives it. That keeps a test
 * free of emission-order races: it can set the position before or after rendering and still observe
 * it shared.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FakeLocationProvider(initialStatus: LocationStatus = LocationStatus.GRANTED) :
    LocationProvider {

  private val mutableStatus = MutableStateFlow(initialStatus)
  override val status: StateFlow<LocationStatus> = mutableStatus.asStateFlow()

  private val position = MutableStateFlow<GeoPosition?>(null)

  /** How many sessions are collecting [positions] right now, to check that GPS collection stops. */
  val activeCollectors: Int
    get() = position.subscriptionCount.value

  /** Sets the current position, replayed to any session collecting [positions]. */
  fun setPosition(newPosition: GeoPosition) {
    position.value = newPosition
  }

  override fun refreshStatus() = Unit

  override fun positions(): Flow<GeoPosition> = status.flatMapLatest {
    if (it == LocationStatus.GRANTED) position.filterNotNull() else emptyFlow()
  }
}
