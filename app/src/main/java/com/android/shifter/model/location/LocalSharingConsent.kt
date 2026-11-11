// Co-authored-by: Claude Opus 4.8 <noreply@anthropic.com>
package com.swent.shifter.model.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The volunteer's just-made "turn sharing off" decision, in memory, shared between Settings and the
 * foreground publisher so turning sharing off stops GPS **at once** - before Settings looks up the
 * teams or writes anything to Firestore, which is what used to keep the GPS running in between.
 *
 * It is not a second source of truth: Firestore stays the one persisted consent, and this only ever
 * forces sharing **off** for the brief gap between the switch moving and Firestore reporting the
 * change back. [applyTo] releases the decision as soon as the real consent confirms it, so the
 * publisher is driven by Firestore again and a later "on", on this or another device, is never held
 * off by a stale local decision.
 *
 * Turning sharing **on** is never forced: an "on" only [release]s a pending "off", so no position
 * is published before the real consent is valid, exactly as [LiveLocationPublisher] requires.
 *
 * One instance is shared through [com.swent.shifter.model.RepositoryProvider]. It holds the single
 * signed-in user's id, so a decision is scoped to its owner: another account's publisher reads
 * [applyTo] for its own id and is unaffected, and signing out leaves nothing that could re-enable
 * or suppress the next user's sharing.
 */
class LocalSharingConsent {

  /**
   * The id of the user whose sharing is locally forced off, or null when no "off" decision holds.
   */
  private val forcedOffFor = MutableStateFlow<String?>(null)

  /** Forces [userId]'s sharing off until the real consent confirms it, or it is [release]d. */
  fun turnOff(userId: String) {
    forcedOffFor.value = userId
  }

  /**
   * Drops any pending "off" decision for [userId], letting the real consent drive the publisher
   * again. Used when the volunteer turns sharing back on, when a failed save is rolled back, and by
   * [applyTo] once the real consent has caught up. A no-op when no decision is held for [userId].
   */
  fun release(userId: String) {
    forcedOffFor.update { current -> if (current == userId) null else current }
  }

  /**
   * [real], the real consent (from [sharingConsent]), forced off while a local "off" decision holds
   * for [userId]: the publisher stops GPS the instant [turnOff] is called, without waiting for any
   * repository call.
   *
   * It keeps every contract [real] already honours:
   * - **No placeholder while the consent loads.** It emits nothing until [real] emits its first
   *   real value; the "off" flag alone never produces a leading `false`.
   * - **No stale re-enabling.** The decision is [release]d only once [real] itself reports the
   *   matching `false`, so the forced `false` is never dropped while [real] still holds an older
   *   `true`: there is no window where GPS resumes from a stale snapshot.
   */
  fun applyTo(real: Flow<Boolean>, userId: String): Flow<Boolean> {
    val forcedOff = forcedOffFor.map { it == userId }.distinctUntilChanged()
    return combine(real, forcedOff) { consent, off ->
          // The real consent has caught up to the local "off": the decision has done its job, so
          // drop it - but only while the real consent is already false, so the forced false is
          // never released onto a stale true.
          if (off && !consent) release(userId)
          consent && !off
        }
        .distinctUntilChanged()
  }
}
