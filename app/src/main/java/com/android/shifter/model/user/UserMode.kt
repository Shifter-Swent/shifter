// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

/**
 * How a user uses Shifter: saved at their first sign-in, from the sign-in screen used, and changed
 * in Settings. Stored by name in [User.mode].
 */
enum class UserMode {
  /** Joins events and works their missions. */
  VOLUNTEER,

  /** Creates and runs events. */
  ORGANIZER,
}
