// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import com.swent.shifter.model.location.GeoPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * A form that places something by coordinates typed in or picked on a map: what the picker's
 * lifecycle, [LocationPicking], reads and writes on its state. The mission and the event forms
 * implement it on their UI states, which keep the fields their screens render.
 *
 * @param S the implementing state, which the two writers return as it is.
 */
interface LocationPickingForm<S : LocationPickingForm<S>> {
  /** The latitude as typed. */
  val latitude: String

  /** The longitude as typed. */
  val longitude: String

  /** Whether the map to pick the point on is open. */
  val isPickingLocation: Boolean

  /** The point picked on the open map, not used until it is confirmed. */
  val locationDraft: GeoPosition?

  /** The point the typed coordinates give, or null while either of them is missing or invalid. */
  val position: GeoPosition?
    get() {
      val lat = parseCoordinate(latitude, GeoPosition.LATITUDE_RANGE) ?: return null
      val lng = parseCoordinate(longitude, GeoPosition.LONGITUDE_RANGE) ?: return null
      return GeoPosition(lat, lng)
    }

  /** This state with the map open or closed, and the given point picked on it. */
  fun withPicker(isPickingLocation: Boolean, locationDraft: GeoPosition?): S

  /** This state with the coordinates written. */
  fun withCoordinates(latitude: String, longitude: String): S
}

/**
 * The lifecycle of a form's map picker (#303, #408), shared by the mission and the event forms so
 * picking a place works the same in both: the map opens on the form's current point, the point
 * picked is a draft until it is confirmed, and closing the map leaves the coordinates as they were.
 *
 * It keeps only where the map opened; the form's [state] keeps what the screen renders.
 */
internal class LocationPicking<S : LocationPickingForm<S>>(private val state: MutableStateFlow<S>) {

  /** The point of the form when the map was opened, so confirming it unmoved changes nothing. */
  private var openedOn: GeoPosition? = null

  /**
   * Opens the map on the point the coordinates give. With no complete point, e.g. a latitude typed
   * and not the longitude yet, it opens with no draft, and the point picked replaces both.
   */
  fun open() {
    openedOn = state.value.position
    state.update { it.withPicker(isPickingLocation = true, locationDraft = openedOn) }
  }

  /** Moves the point picked on the open map, without touching the form yet. */
  fun moveTo(position: GeoPosition) {
    state.update { if (it.isPickingLocation) it.withPicker(true, position) else it }
  }

  /** Closes the map and leaves the coordinates as they were. */
  fun close() {
    state.update { it.withPicker(isPickingLocation = false, locationDraft = null) }
  }

  /**
   * What confirming the map writes: the point picked, as coordinates of five decimals, with the map
   * closed. Null when there is nothing to write, with no point or one left where the map opened, so
   * the coordinates typed keep their precision rather than being rounded to the decimals the map
   * gives; the caller then just closes the map. Applied by the caller through its own edit, so the
   * form is revalidated as for any other change.
   */
  fun confirmed(): ((S) -> S)? {
    val draft = state.value.locationDraft
    if (draft == null || draft == openedOn) return null
    return { form ->
      form
          .withCoordinates(formatCoordinate(draft.latitude), formatCoordinate(draft.longitude))
          .withPicker(isPickingLocation = false, locationDraft = null)
    }
  }
}
