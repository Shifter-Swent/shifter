// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.model

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.swent.shifter.model.authentication.AuthRepositoryFirebase
import com.swent.shifter.model.event.FirestoreEventRepository
import com.swent.shifter.model.membership.MembershipRequestRepositoryFirestore
import com.swent.shifter.model.mission.FirestoreMissionRepository
import com.swent.shifter.model.team.FirestoreTeamRepository
import com.swent.shifter.model.user.UserRepositoryFirestore

/**
 * Builds the [RepositoryProvider] the app runs on.
 *
 * This is the only place that names the Firebase implementations, so the container itself stays a
 * plain holder of interfaces. Keeping the two apart is what lets a unit test build a container from
 * fakes without ever reaching this file, and hence without Firebase.
 */
object RepositoryProviderFactory {

  /**
   * The container the production app is built from, created on first access and kept for the
   * lifetime of the process, so every screen shares the same repositories.
   *
   * The composition root reads it once and injects the repositories each ViewModel needs; nothing
   * below the composition root is meant to reach for it.
   */
  val production: RepositoryProvider by lazy { create(Firebase.firestore, Firebase.auth) }

  /**
   * Builds a provider backed by Firebase, in which every Firestore repository shares [firestore]
   * and the authentication repository is given [auth].
   *
   * One Firestore instance is what makes a batch or a transaction spanning two repositories
   * possible. Both backends are passed in rather than read from `Firebase`, so this is the only
   * place that decides which ones the app runs on, and an instrumented test can point the whole
   * container at the Firestore emulator, the Auth emulator, or just one of the two.
   */
  fun create(firestore: FirebaseFirestore, auth: FirebaseAuth): RepositoryProvider =
      RepositoryProvider(
          authRepository = AuthRepositoryFirebase(auth),
          eventRepository = FirestoreEventRepository(firestore),
          userRepository = UserRepositoryFirestore(firestore),
          membershipRequestRepository = MembershipRequestRepositoryFirestore(firestore),
          missionRepository = FirestoreMissionRepository(firestore),
          teamRepository = FirestoreTeamRepository(firestore),
      )
}
