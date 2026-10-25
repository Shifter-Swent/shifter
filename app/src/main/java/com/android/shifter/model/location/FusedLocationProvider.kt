// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.LocationAvailability
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
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

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

  @OptIn(ExperimentalCoroutinesApi::class)
  override fun positions(): Flow<LatLng> = flow {
    refreshStatus()
    emitAll(status.flatMapLatest { if (it == LocationStatus.GRANTED) updates() else emptyFlow() })
  }

  // Only collected while the status is GRANTED, which readStatus checked just before.
  @SuppressLint("MissingPermission")
  private fun updates(): Flow<LatLng> = callbackFlow {
    val callback =
        object : LocationCallback() {
          override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { trySend(LatLng(it.latitude, it.longitude)) }
          }

          // Location services turned off while collecting: the status then stops the updates.
          override fun onLocationAvailability(availability: LocationAvailability) {
            if (!availability.isLocationAvailable) refreshStatus()
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
