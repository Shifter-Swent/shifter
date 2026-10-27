// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Gives the volunteer's current position, for the automatic mission check-in. ViewModels depend on
 * this interface, never on an implementation, so they are tested with a fake and no GPS.
 *
 * Foreground only: positions are delivered while [positions] is collected, and never in the
 * background. To stop them when the app goes to the background, collect them in a lifecycle-aware
 * way, for instance a ViewModel `stateIn(SharingStarted.WhileSubscribed(...))` collected by the
 * screen with `collectAsStateWithLifecycle`.
 */
interface LocationProvider {

  /**
   * Whether positions can be read. It changes when [refreshStatus] is called, and on its own when
   * location services are turned on or off while [positions] is collected.
   */
  val status: StateFlow<LocationStatus>

  /**
   * Reads the permission and the location services again and updates [status]. The UI calls it
   * after the permission dialog and when the app comes back from the settings, where the user may
   * have changed them.
   */
  fun refreshStatus()

  /**
   * Emits the current position at regular intervals while collected, and stops the updates when the
   * collection is cancelled. It follows [status]: updates run only while it is
   * [LocationStatus.GRANTED] and resume when it becomes so again, so the flow never completes and
   * emits nothing while the location is denied or turned off.
   */
  fun positions(): Flow<GeoPosition>
}
