// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.location

/**
 * What a session of [LiveLocationPublisher] is doing with the volunteer's position, for the
 * indicator that tells them about it (#405).
 *
 * **[SHARED] is only reported once the backend has confirmed a write**: a position merely sent, or
 * queued offline, is [WAITING], never [SHARED].
 *
 * The states are declared from the least to the most urgent for the volunteer, so the state of
 * several sessions together - one per team of each running event - is the greatest of theirs (see
 * [LiveLocationCoordinator.sharingState]): a problem in one team is never hidden behind a success
 * in another. Which of [REFUSED] and [NO_LOCATION_ACCESS] comes first hardly matters: the access is
 * the device's, so every session reports its loss at once, and the two only meet for the moment a
 * session has not read it yet.
 */
enum class LiveLocationSharingState {
  /**
   * Sharing is off: the GPS is not read, nothing is written, and the position already shared is
   * being taken out. Usually the volunteer turned it off, but not always: while their setting
   * cannot be read, the consent flow says off rather than share without it (see
   * [sharingConsent][com.swent.shifter.model.location.sharingConsent]).
   */
  OFF,

  /**
   * The backend confirmed the last position written, so the manager sees it. It stays so until
   * another write or the access says otherwise: with no new position from the GPS, indoors for
   * instance, the one the manager sees grows older while this is still reported. Offline too, until
   * the next write is due and fails to be confirmed, up to a couple of minutes for a volunteer
   * standing still.
   */
  SHARED,

  /**
   * Sharing is on and the position can be read, but none is confirmed yet: either nothing has been
   * written since the session started or the access came back (the GPS may not have a position
   * yet), or the last write was not confirmed (no connection, no answer in time, an unexpected
   * backend failure). A write not confirmed may still reach the backend later, so this does not
   * mean nothing will.
   */
  WAITING,

  /**
   * Sharing is on, but the app cannot read the position: the location permission is not granted or
   * location services are off (see [LocationStatus]). Unlike [WAITING], it does not resolve on its
   * own: the volunteer has to change it on their phone.
   */
  NO_LOCATION_ACCESS,

  /**
   * The backend refused the last position written, so the manager does not see it. The causes
   * [LiveLocationRepositoryException.PermissionDenied] lists all land here. The next position worth
   * a write tries again, so this lasts at least [LiveLocationPublisher.MIN_INTERVAL], and up to
   * [LiveLocationPublisher.REFRESH_INTERVAL] for a volunteer standing still.
   */
  REFUSED,
}
