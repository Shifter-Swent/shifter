// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/**
 * In-memory [LocationProvider] for unit tests: a test moves the volunteer with [emit] and changes
 * the permission with [setStatus], or with [deviceStatus] to change it only on the next
 * [refreshStatus], like a user who changes it in the settings.
 *
 * Like the Android provider, [positions] reads the status again when collected and delivers
 * positions only while it is [LocationStatus.GRANTED].
 */
class FakeLocationProvider(initialStatus: LocationStatus = LocationStatus.GRANTED) :
    LocationProvider {

  private val mutableStatus = MutableStateFlow(initialStatus)
  override val status: StateFlow<LocationStatus> = mutableStatus.asStateFlow()

  /** The status the next [refreshStatus] reads, as the device settings would give it. */
  var deviceStatus: LocationStatus = initialStatus

  /** How many times [refreshStatus] was called. */
  var refreshCount = 0
    private set

  private val updates = MutableSharedFlow<LatLng>()

  /** How many collectors are listening for positions right now, to check the updates stop. */
  val activeCollectors: Int
    get() = updates.subscriptionCount.value

  /** Changes the status right away, as if it was refreshed after a change in the settings. */
  fun setStatus(newStatus: LocationStatus) {
    deviceStatus = newStatus
    mutableStatus.value = newStatus
  }

  /** Delivers [position] to every collector; it is lost if nobody is listening. */
  suspend fun emit(position: LatLng) = updates.emit(position)

  override fun refreshStatus() {
    refreshCount++
    mutableStatus.value = deviceStatus
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun positions(): Flow<LatLng> = flow {
    refreshStatus()
    emitAll(status.flatMapLatest { if (it == LocationStatus.GRANTED) updates else emptyFlow() })
  }
}
