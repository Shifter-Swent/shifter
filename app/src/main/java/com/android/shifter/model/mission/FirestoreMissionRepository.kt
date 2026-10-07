// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.google.firebase.firestore.FirebaseFirestore
import com.swent.shifter.model.event.EventSchema
import kotlinx.coroutines.tasks.await

/**
 * [MissionRepository] backed by Cloud Firestore, storing each mission in the subcollection of its
 * event: `/events/{eventId}/missions/{missionId}`.
 *
 * All Firebase types stay inside this class and the [toMission] / [toFirestoreMap] mappers: callers
 * only ever see domain models. [firestore] is injected so tests can point it at the emulator.
 */
class FirestoreMissionRepository(private val firestore: FirebaseFirestore) : MissionRepository {

  private fun missionsOf(eventId: String) =
      firestore
          .collection(EventSchema.COLLECTION)
          .document(eventId)
          .collection(MissionSchema.COLLECTION)

  /**
   * Writes [mission] under a freshly generated document id in the subcollection of its event.
   *
   * [Mission.createdAt] is persisted as the caller supplied it: the repository does not invent
   * time, which keeps writes deterministic and testable.
   */
  override suspend fun createMission(mission: Mission): Mission {
    // Firestore would also reject an empty id, but with a message that does not say which one.
    require(mission.eventId.isNotEmpty()) { "A mission must belong to an event" }
    val document = missionsOf(mission.eventId).document()
    val persisted = mission.copy(id = document.id)
    document.set(persisted.toFirestoreMap()).await()
    return persisted
  }

  override suspend fun getMission(eventId: String, missionId: String): Mission? =
      missionsOf(eventId).document(missionId).get().await().takeIf { it.exists() }?.toMission()

  override suspend fun getMissionsByEvent(eventId: String): List<Mission> =
      missionsOf(eventId).get().await().documents.map { it.toMission() }
}
