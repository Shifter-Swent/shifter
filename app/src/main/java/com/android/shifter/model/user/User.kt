// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

import java.time.Instant

/**
 * A Shifter user profile, stored in Firestore at `users/{uid}`.
 *
 * @property uid Firebase Auth uid, also used as the document id.
 * @property displayName Name shown to the organiser and the team.
 * @property email Contact email.
 * @property locationSharingEnabled The volunteer's global consent to share their location. Opt-in,
 *   so `false` by default.
 * @property createdAt Account creation date, set by the server. `null` until the write is
 *   acknowledged.
 * @property mode How the user uses Shifter. `null` for a profile that has none yet; saved at
 *   sign-in when missing, and changed in Settings.
 */
data class User(
    val uid: String,
    val displayName: String,
    val email: String,
    val locationSharingEnabled: Boolean = false,
    val createdAt: Instant? = null,
    val mode: UserMode? = null,
)
