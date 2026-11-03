// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model.location

/**
 * Errors thrown by [LiveLocationRepository] when the backend fails. Implementations translate their
 * backend errors into these, so ViewModels can handle failures without depending on Firebase.
 */
sealed class LiveLocationRepositoryException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

  /**
   * The rules refused the operation. Several causes land here and the backend does not tell them
   * apart: location sharing being off, no longer being in the team, the event not running, a reader
   * who is neither the manager of the team nor the organizer, and a measure older than the one
   * already stored.
   *
   * It therefore does not mean the consent was revoked, and retrying the same measure will not help
   * either. In particular a publisher must not delete the stored position on every denial: doing so
   * when a measure was only refused for being out of order would throw away a position that is
   * still valid and still being shared. Telling a genuine consent or membership change from a
   * rejected measure takes the context the publisher already has, such as the volunteer's current
   * setting and the last measure it sent, rather than this exception alone.
   *
   * One device on its own is enough to produce that out of order case: the rules tolerate a measure
   * up to 5 min ahead, so a clock running 4 min fast stores a position dated in the future, and
   * every real measure taken once the clock is corrected looks older than it and is refused until
   * the stored time is caught up with. Sending measures in the order they are taken is therefore
   * not enough to rule a denial out.
   */
  class PermissionDenied(cause: Throwable? = null) :
      LiveLocationRepositoryException("Access to the live locations was denied", cause)

  /**
   * The backend could not be reached, typically because the device is offline. Retrying is fine.
   */
  class Unavailable(cause: Throwable? = null) :
      LiveLocationRepositoryException("The live location service is unavailable", cause)

  /** Any other backend failure. */
  class Unknown(cause: Throwable? = null) :
      LiveLocationRepositoryException("Unexpected error while accessing the live locations", cause)
}
