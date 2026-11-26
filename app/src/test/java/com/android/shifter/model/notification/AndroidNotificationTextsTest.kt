// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import androidx.test.core.app.ApplicationProvider
import com.swent.shifter.R
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidNotificationTextsTest {

  private val texts = AndroidNotificationTexts(ApplicationProvider.getApplicationContext())
  private val defaultZone = TimeZone.getDefault()

  @After
  fun restoreZone() {
    TimeZone.setDefault(defaultZone)
  }

  @Test
  fun string_isTheResourceFormattedWithItsArguments() {
    assertEquals(
        "Missions starting in the next 24 h that still have open spots.",
        texts.string(R.string.notification_channel_open_spots_description, 24),
    )
  }

  @Test
  fun plural_picksTheFormOfTheCount() {
    assertEquals(
        "Set up at 08:00 still needs 1 volunteer.",
        texts.plural(R.plurals.notification_open_spots_text, 1, "Set up", "08:00", 1),
    )
    assertEquals(
        "Set up at 08:00 still needs 3 volunteers.",
        texts.plural(R.plurals.notification_open_spots_text, 3, "Set up", "08:00", 3),
    )
  }

  @Test
  fun zone_followsThePhonesZone_whenItChanges() {
    TimeZone.setDefault(TimeZone.getTimeZone("Europe/Zurich"))
    assertEquals(ZoneId.of("Europe/Zurich"), texts.zone)

    TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
    assertEquals(ZoneId.of("Asia/Tokyo"), texts.zone)
  }
}
