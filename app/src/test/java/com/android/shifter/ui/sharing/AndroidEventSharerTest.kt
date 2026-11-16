// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.sharing

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidEventSharerTest {

  private val application = RuntimeEnvironment.getApplication()

  @Test
  fun share_opensTheShareSheetWithTheEventAndItsCode() {
    AndroidEventSharer(application).share("City Marathon 2025", "ABC234")

    val chooser = shadowOf(application).nextStartedActivity
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    // Started from the application, which is not an activity.
    assertTrue(chooser.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals(Intent.ACTION_SEND, send.action)
    assertEquals("text/plain", send.type)
    assertEquals(
        "Join City Marathon 2025 on Shifter with the code ABC234",
        send.getStringExtra(Intent.EXTRA_TEXT),
    )
  }
}
