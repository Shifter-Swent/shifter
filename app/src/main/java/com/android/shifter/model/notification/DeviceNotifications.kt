// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.notification

import android.content.Context
import com.swent.shifter.model.RepositoryProvider
import com.swent.shifter.model.RepositoryProviderFactory

/**
 * The notifications of this phone: the [engine] built from [repositories] and the Android notifier,
 * stores and texts, with the checks the app has. Use [of], so the app and the background runs share
 * one engine, whose runs never overlap.
 */
class DeviceNotifications(context: Context, repositories: RepositoryProvider) {

  /** The application's, so a caller passing an Activity never keeps it in memory. */
  private val context: Context = context.applicationContext

  private val texts = AndroidNotificationTexts(this.context)

  val engine =
      NotificationEngine(
          inbox = repositories.notificationInbox,
          preferences = repositories.notificationPreferences,
          users = repositories.userRepository,
          // Each kind's format comes with the task that writes it: #595, #603, #599, #617, #618.
          formats = emptyList(),
          checks =
              listOf(
                  OpenSpots(repositories.eventRepository, repositories.missionRepository, texts)
              ),
          notifier = AndroidNotifier(this.context),
          shownItems = { userId -> SharedPreferencesShownItems(this.context, userId) },
          seenStore = { userId, kind -> SharedPreferencesSeenStore(this.context, userId, kind) },
          texts = texts,
      )

  companion object {
    @Volatile private var process: DeviceNotifications? = null

    /** The one of this process, built from the production repositories at the first call. */
    fun of(context: Context): DeviceNotifications =
        process
            ?: synchronized(this) {
              process
                  ?: DeviceNotifications(context, RepositoryProviderFactory.production).also {
                    process = it
                  }
            }
  }
}
