// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.swent.shifter.model.authentication.AuthRepositoryFirebase
import com.swent.shifter.model.checkin.FirestoreCheckInRepository
import com.swent.shifter.model.event.FirestoreEventRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryFirestore
import com.swent.shifter.model.mission.FirestoreMissionRepository
import com.swent.shifter.model.team.FirestoreTeamRepository
import com.swent.shifter.model.user.UserRepositoryFirestore

/**
 * Builds the [RepositoryProvider] the app runs on.
 *
 * Production repository construction is centralized here, which is what keeps [RepositoryProvider]
 * a plain holder of interfaces that a test can fill with fakes.
 */
object RepositoryProviderFactory {

  /**
   * The container the production app is built from: one instance for the whole process, created on
   * first access and backed by the default `Firebase.firestore` and `Firebase.auth`.
   *
   * Only `MainActivity` reads it: `ShifterNavHost` takes its [RepositoryProvider] as a required
   * parameter with no default, so instrumented tests pass `create(FirestoreEmulator.firestore,
   * AuthEmulator.auth)` and cannot reach production by forgetting it. Tests that launch
   * `MainActivity` itself must initialize the emulator instances before the activity starts.
   * Otherwise this one initializes the shared Firestore instance against the production backend,
   * and every later `useEmulator` call in the same process throws.
   */
  val production: RepositoryProvider by lazy { create(Firebase.firestore, Firebase.auth) }

  /**
   * Builds a provider in which every Firestore repository shares [firestore], which is what lets a
   * batch or a transaction span two of them, and the authentication repository uses [auth].
   *
   * Both are parameters rather than read from `Firebase`, so a test can point the whole container
   * at the emulators.
   */
  fun create(firestore: FirebaseFirestore, auth: FirebaseAuth): RepositoryProvider =
      RepositoryProvider(
          authRepository = AuthRepositoryFirebase(auth),
          eventRepository = FirestoreEventRepository(firestore),
          userRepository = UserRepositoryFirestore(firestore),
          membershipRequestRepository = MembershipRequestRepositoryFirestore(firestore),
          missionRepository = FirestoreMissionRepository(firestore),
          teamRepository = FirestoreTeamRepository(firestore),
          checkInRepository = FirestoreCheckInRepository(firestore, auth),
      )
}
