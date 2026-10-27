// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest

/**
 * [LocationProvider] backed by the fused location provider of Google Play services.
 *
 * Only the fine permission counts as granted: an approximate position is too coarse to tell whether
 * a volunteer is in a mission's zone, so the coarse permission alone is [LocationStatus.DENIED].
 *
 * @param intervalMillis how often a position is requested while [positions] is collected.
 */
class FusedLocationProvider(
    context: Context,
    private val intervalMillis: Long = UPDATE_INTERVAL_MILLIS,
) : LocationProvider {

  private val appContext = context.applicationContext
  private val client = LocationServices.getFusedLocationProviderClient(appContext)
  private val locationManager = appContext.getSystemService(LocationManager::class.java)

  private val mutableStatus = MutableStateFlow(readStatus())
  override val status: StateFlow<LocationStatus> = mutableStatus.asStateFlow()

  override fun refreshStatus() {
    mutableStatus.value = readStatus()
  }

  /**
   * Also refreshes the status whenever location services are turned on or off while collected, for
   * instance from the quick settings, which does not pause the activity and so never triggers the
   * UI's refresh. Conflated: a slow collector only needs the latest position, not a backlog.
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  override fun positions(): Flow<GeoPosition> = channelFlow {
    val modeReceiver =
        object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) = refreshStatus()
        }
    ContextCompat.registerReceiver(
        appContext,
        modeReceiver,
        IntentFilter(LocationManager.MODE_CHANGED_ACTION),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    try {
      refreshStatus()
      status
          .flatMapLatest { if (it == LocationStatus.GRANTED) updates() else emptyFlow() }
          .collect { send(it) }
    } finally {
      appContext.unregisterReceiver(modeReceiver)
    }
  }
      .conflate()

  // Only collected while the status is GRANTED, which readStatus checked just before.
  @SuppressLint("MissingPermission")
  private fun updates(): Flow<GeoPosition> = callbackFlow {
    val callback =
        object : LocationCallback() {
          override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { trySend(GeoPosition(it.latitude, it.longitude)) }
          }
        }
    val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis).build()
    client.requestLocationUpdates(request, callback, Looper.getMainLooper()).addOnFailureListener {
      close(it)
    }
    awaitClose { client.removeLocationUpdates(callback) }
  }

  private fun readStatus(): LocationStatus {
    val granted =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    return when {
      !granted -> LocationStatus.DENIED
      !LocationManagerCompat.isLocationEnabled(locationManager) -> LocationStatus.LOCATION_OFF
      else -> LocationStatus.GRANTED
    }
  }

  private companion object {
    /** Often enough to notice a volunteer entering a mission's zone on foot. */
    const val UPDATE_INTERVAL_MILLIS = 10_000L
  }
}
