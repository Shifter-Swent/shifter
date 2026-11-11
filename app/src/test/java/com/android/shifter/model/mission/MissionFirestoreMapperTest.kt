// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import com.swent.shifter.model.location.GeoPosition
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Keeps the fields [toEditableFieldsMap] writes in line with those [Mission.withEditableFieldsOf]
 * copies, so the Firestore repository saves what the fake does.
 */
class MissionFirestoreMapperTest {

  @Test
  fun toEditableFieldsMap_holdsTheFieldsWithEditableFieldsOfCopies() {
    val stored =
        Mission(
            id = "mission-1",
            eventId = "event-1",
            title = "Trier les dons",
            description = "Dans la salle.",
            teamId = "team-1",
            volunteersNeeded = 2,
            startAt = Instant.parse("2030-06-20T12:00:00Z"),
            endAt = Instant.parse("2030-06-20T13:00:00Z"),
            location = MissionLocation("Salle", GeoPosition(46.5, 6.5)),
            assigneeIds = listOf("user-1"),
            createdAt = Instant.parse("2030-05-01T00:00:00Z"),
            completed = false,
        )
    // Differs from stored on every field.
    val edited =
        Mission(
            id = "mission-2",
            eventId = "event-2",
            title = "Accueillir",
            description = "À l'entrée.",
            teamId = "team-2",
            volunteersNeeded = 4,
            startAt = Instant.parse("2030-06-21T08:00:00Z"),
            endAt = Instant.parse("2030-06-21T10:00:00Z"),
            location = null,
            assigneeIds = listOf("user-2", "user-3"),
            createdAt = Instant.parse("2030-05-02T00:00:00Z"),
            completed = true,
        )

    assertEquals(
        stored.withEditableFieldsOf(edited).toFirestoreMap(),
        stored.toFirestoreMap() + edited.toEditableFieldsMap(),
    )
  }
}
