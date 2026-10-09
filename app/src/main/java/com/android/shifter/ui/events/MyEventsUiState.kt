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
 * over. A single enum on purpose: it describes what the shared card displays, not a domain status.
 */
enum class EventBadge {
  CONFIRMED,
  PENDING_APPROVAL,
  IN_PREPARATION,
  ONGOING,
  ENDED,
}

/** The left part of an event card footer: the user's role, or why they have none yet. */
enum class EventCardFooter {
  VOLUNTEER,
  ORGANIZER,
  AWAITING_APPROVAL,
}

/**
 * Everything an event card displays.
 *
 * This model belongs to the UI layer and never references the domain `Event`, so the screens can be
 * built and tested independently of the repositories. Texts that come from a fixed set ([badge],
 * [footer]) are enums, so the components can read them from the string resources.
 */
data class EventCardUi(
    val id: String,
    val title: String,
    val dateLabel: String,
    val locationLabel: String,
    val timeLabel: String,
    val badge: EventBadge,
    val footer: EventCardFooter,
)

/**
 * State of a My Events screen, shared by the staff and the organizer views.
 *
 * Failures are flags rather than messages: the screen shows its own generic, translated text, so a
 * backend error never reaches the user as is.
 *
 * @property loadFailed the events could not be loaded.
 * @property withdrawFailed the last withdrawal failed (staff view only).
 */
data class MyEventsUiState(
    val selectedTab: EventTab = EventTab.UPCOMING,
    val upcoming: List<EventCardUi> = emptyList(),
    val past: List<EventCardUi> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val withdrawFailed: Boolean = false,
) {
  /** The cards of the selected tab. */
  val visibleEvents: List<EventCardUi>
    get() =
        when (selectedTab) {
          EventTab.UPCOMING -> upcoming
          EventTab.PAST -> past
        }
}
