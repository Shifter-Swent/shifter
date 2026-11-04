// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.shifter.model.location.GeoPosition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShifterMapTest {
  @get:Rule val compose = createComposeRule()

  // Tapping a marker happens inside the native map view, which Compose tests can't reach. The
  // missions screen will test what a tap does through its ViewModel.
  @Test
  fun map_isDisplayedWithMarkers() {
    val markers =
        listOf(
            MapMarker("epfl", GeoPosition(46.5191, 6.5668), "EPFL", "Rolex Learning Center"),
            MapMarker("ouchy", GeoPosition(46.5069, 6.6266), "Ouchy"),
        )

    compose.setContent {
      ShifterMap(markers = markers, onMarkerClick = {}, modifier = Modifier.fillMaxSize())
    }

    compose.onNodeWithTag(ShifterMapTestTags.MAP).assertIsDisplayed()
  }

  // The picker's behaviour is covered over a stand-in map by LocationPickerTest; this only checks
  // that it draws the real one.
  @Test
  fun locationPicker_drawsTheRealMap() {
    compose.setContent {
      LocationPicker(
          selected = GeoPosition(46.5191, 6.5668),
          onSelect = {},
          modifier = Modifier.fillMaxSize(),
      )
    }

    compose.onNodeWithTag(ShifterMapTestTags.MAP).assertIsDisplayed()
    compose.onNodeWithTag(LocationPickerTestTags.SELECTED).assertIsDisplayed()
  }
}
