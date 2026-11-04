// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

/**
 * How a user uses Shifter, chosen once after their first sign-in and never changed afterwards.
 * Stored by name in [User.mode].
 */
enum class UserMode {
  /** Joins events and works their missions. */
  VOLUNTEER,

  /** Creates and runs events. */
  ORGANIZER,
}
