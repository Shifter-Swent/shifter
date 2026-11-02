// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.event

/** The shape of the [Event.joinCode] volunteers type to join an event. */
object JoinCode {
  const val LENGTH = 6

  /** Digits and upper-case letters, without the pairs users confuse: 0/O, 1/I/L. */
  const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

  /**
   * Turns what the volunteer typed or pasted into a code: letters and digits only, upper-cased and
   * cut to [LENGTH], so a pasted `abc-234` fills every box. Characters outside [ALPHABET] are kept,
   * so a mistyped `0` shows up as an unknown code rather than silently disappearing.
   */
  fun normalize(input: String): String =
      input.filter(Char::isLetterOrDigit).uppercase().take(LENGTH)

  /** Whether [code] has all of its characters, so it can be looked up. */
  fun isComplete(code: String): Boolean = code.length == LENGTH
}
