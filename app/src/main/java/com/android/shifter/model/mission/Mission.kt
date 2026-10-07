// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.mission

import java.time.Instant

/**
 * A mission of an event: a task with a time slot that needs a number of staff, such as sorting the
 * collected food between 14:00 and 15:30.
 *
 * The model stays free of Firebase types so it can be used by ViewModels and tested without a
 * backend.
 *
 * @property id the backend document id, empty until the mission has been created.
 * @property eventId id of the event the mission belongs to.
 * @property sectorId id of the event sector the mission is attached to, or null for "General", the
 *   missions attached to no sector. Sectors are defined by the organizer when creating the event.
 * @property staffNeeded how many people the mission needs.
 * @property startAt when the mission starts, as a time-zone independent instant.
 * @property endAt when the mission ends, as a time-zone independent instant.
 * @property assigneeIds ids of the people assigned to the mission, none when it is created.
 * @property createdAt when the mission was first persisted.
 */
data class Mission(
    val id: String = "",
    val eventId: String,
    val title: String,
    val description: String,
    val sectorId: String? = null,
    val staffNeeded: Int,
    val startAt: Instant,
    val endAt: Instant,
    val assigneeIds: List<String> = emptyList(),
    val createdAt: Instant,
)
