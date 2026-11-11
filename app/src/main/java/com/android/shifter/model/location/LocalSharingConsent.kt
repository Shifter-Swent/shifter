// Co-authored-by: Claude Opus 4.8 <noreply@anthropic.com>
// Co-authored-by: Claude Fable 5.1 <noreply@anthropic.com>
package com.swent.shifter.model.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.withIndex

/**
 * The volunteer's just-made "turn sharing off" decision, in memory, shared between Settings and the
 * foreground publisher so turning sharing off stops GPS **at once** - before Settings looks up the
 * teams or writes anything to Firestore, which is what used to keep the GPS running in between.
 *
 * It is not a second source of truth: Firestore stays the one persisted consent, and this only
 * forces sharing **off** for the brief gap between the switch moving and Firestore reporting the
 * change back. The decision lives in two steps, which is what keeps that gap safe at both ends:
 * - **[turnOff] - pending.** Sharing is forced off at once, and **no value on the consent stream
 *   ends it**: a fail-closed `false` the observation emits on a failure, or a stale `true` it still
 *   holds, are both ignored, so neither can resume GPS while the write is in flight.
 * - **[confirmOff] - confirmed.** Once Settings' write has landed (or is queued offline), the
 *   **next fresh value the publisher observes** ends the decision and hands control back to
 *   Firestore. Usually that is a newer `true` saved meanwhile on another device, which resumes GPS:
 *   online, Firestore commits the write to its local cache before the server answers, so the
 *   write's own `false` reaches the publisher while the decision is still pending and is ignored as
 *   such, and the confirmation that follows the answer repeats nothing on the distinct stream. The
 *   decision then stays confirmed, harmlessly (sharing reads off either way), until that newer
 *   value. Should the `false` reach the publisher only after the confirmation (an observation
 *   lagging the write), it ends the decision itself, with no window: GPS stays off as the stream
 *   reaches `false`. A phase change alone never ends it, so the stale `true` cached before the
 *   write is never used to resume GPS.
 *
 * [release] ends it outright, with no wait, for the two cases that supersede it directly: the
 * volunteer turning sharing back on, and a failed save rolled back. Turning on is never
 * optimistic - it only drops a pending off and lets Firestore drive, so no position is published
 * before the real consent is valid, exactly as [LiveLocationPublisher] requires.
 *
 * One instance is shared through [com.swent.shifter.model.RepositoryProvider]. It holds the single
 * signed-in user's id, so a decision is scoped to its owner: another account's publisher reads
 * [applyTo] for its own id and is unaffected, and signing out leaves nothing that could re-enable
 * or suppress the next user's sharing.
 */
class LocalSharingConsent {

  /**
   * How far a forced-off decision has got: forced off and waiting for the write, then confirmed.
   */
  private enum class Phase {
    PENDING,
    CONFIRMED,
  }

  private data class Decision(val userId: String, val phase: Phase)

  /** The forced-off decision in effect, or null when none holds. At most one user at a time. */
  private val decision = MutableStateFlow<Decision?>(null)

  /** Forces [userId]'s sharing off at once, pending the write; no stream value ends it yet. */
  fun turnOff(userId: String) {
    decision.value = Decision(userId, Phase.PENDING)
  }

  /**
   * Marks [userId]'s off write as landed, or queued offline: from now the next fresh value the
   * publisher observes ends the decision and Firestore drives again. The write's own `false` has
   * usually been observed, and ignored, while the decision was still pending (see the class
   * documentation), so that value is normally a later `true`: the decision outlives the write, but
   * never past the next consent value, and reads off meanwhile exactly as Firestore does. A no-op
   * unless a pending off is held for [userId].
   */
  fun confirmOff(userId: String) {
    decision.update {
      if (it?.userId == userId && it.phase == Phase.PENDING) Decision(userId, Phase.CONFIRMED)
      else it
    }
  }

  /**
   * Ends any decision for [userId] outright, letting the real consent drive the publisher again.
   * Used when the volunteer turns sharing back on and when a failed save is rolled back. A no-op
   * when no decision is held for [userId].
   */
  fun release(userId: String) {
    decision.update { current -> if (current?.userId == userId) null else current }
  }

  /**
   * [real], the real consent (from [sharingConsent]), forced off while a decision for [userId]
   * holds: the publisher stops GPS the instant [turnOff] is called, without waiting for any
   * repository call.
   *
   * No placeholder while the consent loads: it emits nothing until [real] emits its first real
   * value, since a decision alone never produces a leading `false`.
   */
  fun applyTo(real: Flow<Boolean>, userId: String): Flow<Boolean> {
    val phases =
        decision.map { if (it?.userId == userId) it.phase else null }.distinctUntilChanged()
    // Per collection: the index tells a fresh value of [real] apart from the same value re-paired
    // by a phase change, so the decision ends only on a value the publisher really observed.
    var lastIndex = -1
    return combine(real.withIndex(), phases) { (index, consent), phase ->
          val freshReal = index != lastIndex
          lastIndex = index
          // Once confirmed, the first value observed from then on is the persisted truth after the
          // write (a newer on, or its own off when that arrives only now): the decision ends and
          // Firestore drives again. A stale cached value, re-paired by a phase change with no new
          // observation, never ends it.
          if (freshReal && phase == Phase.CONFIRMED) release(userId)
          consent && phase == null
        }
        .distinctUntilChanged()
  }
}
