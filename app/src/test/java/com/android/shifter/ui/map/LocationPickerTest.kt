// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.maps.android.compose.CameraPositionState
import com.swent.shifter.model.location.GeoPosition
import com.swent.shifter.ui.theme.ShifterTheme
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The picker drawn over a stand-in map, since a tap inside the native map cannot be made from a
 * Compose test: the stand-in records what the picker gives it, and has one button per point to tap.
 */
@RunWith(AndroidJUnit4::class)
class LocationPickerTest {

  @get:Rule val compose = createComposeRule()

  private val defaultLocale = Locale.getDefault()

  @After fun restoreLocale() = Locale.setDefault(defaultLocale)

  /** The markers the picker last gave the map. */
  private var markers: List<MapMarker> = emptyList()

  /** The camera the picker opened the map with. */
  private var camera: CameraPositionState? = null

  private val fakeMap: PickerMap = { markers, onMapClick, cameraPositionState ->
    this.markers = markers
    camera = cameraPositionState
    // In a column, so each button is tapped on its own rather than through the one above it.
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
      Button(onClick = { onMapClick(EPFL) }, Modifier.testTag(TAP_EPFL)) { Text("EPFL") }
      Button(onClick = { onMapClick(OUCHY) }, Modifier.testTag(TAP_OUCHY)) { Text("Ouchy") }
    }
  }

  /** The picker with its selection kept here, as a caller would keep it. */
  private fun show(
      initialSelection: GeoPosition? = null,
      initialCenter: GeoPosition? = null,
      onSelect: (GeoPosition) -> Unit = {},
  ) {
    compose.setContent {
      var selected by remember { mutableStateOf(initialSelection) }
      ShifterTheme {
        LocationPicker(
            selected = selected,
            onSelect = {
              selected = it
              onSelect(it)
            },
            modifier = Modifier,
            initialCenter = initialCenter,
            map = fakeMap,
        )
      }
    }
  }

  @Test
  fun opening_withoutASelection_asksToTapTheMapAndShowsNoMarker() {
    show()

    compose
        .onNodeWithTag(LocationPickerTestTags.HINT)
        .assertTextEquals("Tap the map to place the location")
    compose.onNodeWithTag(LocationPickerTestTags.SELECTED).assertDoesNotExist()
    assertEquals(emptyList<MapMarker>(), markers)
  }

  @Test
  fun tappingTheMap_selectsThePointAndPlacesTheMarkerThere() {
    var chosen: GeoPosition? = null
    show(onSelect = { chosen = it })

    compose.onNodeWithTag(TAP_EPFL).performClick()
    compose.waitForIdle()

    assertEquals(EPFL, chosen)
    assertEquals(listOf(SELECTION_MARKER_ID), markers.map { it.id })
    assertEquals(EPFL, markers.single().position)
    compose
        .onNodeWithTag(LocationPickerTestTags.SELECTED)
        .assertTextEquals("Selected: 46.51910, 6.56680")
    compose.onNodeWithTag(LocationPickerTestTags.HINT).assertDoesNotExist()
  }

  @Test
  fun tappingElsewhere_movesTheOneMarker() {
    var chosen: GeoPosition? = null
    show(onSelect = { chosen = it })

    compose.onNodeWithTag(TAP_EPFL).performClick()
    compose.onNodeWithTag(TAP_OUCHY).performClick()
    compose.waitForIdle()

    assertEquals(OUCHY, chosen)
    assertEquals(listOf(OUCHY), markers.map { it.position })
    compose
        .onNodeWithTag(LocationPickerTestTags.SELECTED)
        .assertTextEquals("Selected: 46.50690, 6.62660")
  }

  @Test
  fun opening_withASelection_showsItAndOpensTheCameraOnIt() {
    show(initialSelection = OUCHY, initialCenter = EPFL)

    compose
        .onNodeWithTag(LocationPickerTestTags.SELECTED)
        .assertTextEquals("Selected: 46.50690, 6.62660")
    assertEquals(listOf(OUCHY), markers.map { it.position })
    // The selection wins over the centre the caller suggested.
    assertEquals(OUCHY.toLatLng(), camera?.position?.target)
  }

  @Test
  fun opening_withOnlyACentre_opensTheCameraThereWithoutAMarker() {
    show(initialCenter = EPFL)

    assertEquals(EPFL.toLatLng(), camera?.position?.target)
    assertEquals(emptyList<MapMarker>(), markers)
  }

  @Test
  fun opening_withNothing_opensTheCameraOnTheDefaultPlace() {
    show()

    assertEquals(DEFAULT_CENTER, camera?.position?.target)
  }

  @Test
  fun selecting_doesNotMoveTheCamera() {
    show(initialCenter = EPFL)

    compose.onNodeWithTag(TAP_OUCHY).performClick()
    compose.waitForIdle()

    // The view stays where the user was looking, so it does not jump under their finger.
    assertEquals(EPFL.toLatLng(), camera?.position?.target)
  }

  @Test
  fun picker_tellsScreenReadersWhatItIs() {
    show()

    compose
        .onNodeWithTag(LocationPickerTestTags.PICKER)
        .assertIsDisplayed()
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Map to pick a place. Tap it to place the location."),
            )
        )
  }

  @Test
  fun selecting_onAFrenchDevice_stillWritesADecimalPoint() {
    Locale.setDefault(Locale.FRANCE)
    show()

    compose.onNodeWithTag(TAP_OUCHY).performClick()

    compose
        .onNodeWithTag(LocationPickerTestTags.SELECTED)
        .assertTextEquals("Selected: 46.50690, 6.62660")
  }

  private companion object {
    const val TAP_EPFL = "tapEpfl"
    const val TAP_OUCHY = "tapOuchy"
    val EPFL = GeoPosition(46.5191, 6.5668)
    val OUCHY = GeoPosition(46.5069, 6.6266)
  }
}
