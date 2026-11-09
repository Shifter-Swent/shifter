// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.event

import android.content.ClipboardManager
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import androidx.test.platform.app.InstrumentationRegistry

/**
 * A [Clipboard] that keeps what is copied in memory, so a test reads it back without touching the
 * device's clipboard, which Android only lets the app in focus read.
 *
 * @property failure when set, every copy throws it, like a device refusing access to its clipboard.
 */
class FakeClipboard(var failure: Exception? = null) : Clipboard {

  /** The last entry copied, or null when nothing was. */
  var entry: ClipEntry? = null
    private set

  /** The text of the last entry copied, or null when nothing was. */
  val text: String?
    get() = entry?.clipData?.getItemAt(0)?.text?.toString()

  override suspend fun getClipEntry(): ClipEntry? = entry

  override suspend fun setClipEntry(clipEntry: ClipEntry?) {
    failure?.let { throw it }
    entry = clipEntry
  }

  // Never used by the screens under test; given only because the interface requires it.
  override val nativeClipboard: NativeClipboard =
      InstrumentationRegistry.getInstrumentation()
          .targetContext
          .getSystemService(ClipboardManager::class.java)
}
