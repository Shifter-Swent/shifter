// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

/** The two tabs of a My Events screen. */
enum class EventTab {
  UPCOMING,
  PAST,
}

/**
 * The status pill shown on an event card.
 *
 * Staff cards show the user's participation status ([CONFIRMED], [PENDING_APPROVAL]), organizer
 * cards show the event lifecycle ([IN_PREPARATION], [ONGOING]). Both show [ENDED] once the event is
 * over.
 */
enum class EventBadge(val label: String) {
  CONFIRMED("Confirmed"),
  PENDING_APPROVAL("Pending approval"),
  IN_PREPARATION("In preparation"),
  ONGOING("Ongoing"),
  ENDED("Ended"),
}

/**
 * Everything an event card displays, already formatted.
 *
 * This model belongs to the UI layer and never references the domain `Event`, so the screens can be
 * built and tested before the repositories exist.
 *
 * @property footerLabel the left part of the card footer, e.g. "Role: Volunteer".
 */
data class EventCardUi(
    val id: String,
    val title: String,
    val dateLabel: String,
    val locationLabel: String,
    val timeLabel: String,
    val badge: EventBadge,
    val footerLabel: String,
)

/** State of a My Events screen, shared by the staff and the organizer views. */
data class MyEventsUiState(
    val selectedTab: EventTab = EventTab.UPCOMING,
    val upcoming: List<EventCardUi> = emptyList(),
    val past: List<EventCardUi> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
) {
  /** The cards of the selected tab. */
  val visibleEvents: List<EventCardUi>
    get() =
        when (selectedTab) {
          EventTab.UPCOMING -> upcoming
          EventTab.PAST -> past
        }
}
