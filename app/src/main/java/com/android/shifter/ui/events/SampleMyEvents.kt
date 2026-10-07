// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

/** The events of the Figma mock-ups, for previews and tests until real data is wired in. */
object SampleMyEvents {

  val staff: List<EventCardUi> =
      listOf(
          EventCardUi(
              id = "city-marathon",
              title = "City Marathon 2025",
              dateLabel = "Sat 14 Jun 2025",
              locationLabel = "Lyon, Place Bellecour",
              timeLabel = "08:00 – 14:00",
              badge = EventBadge.CONFIRMED,
              footerLabel = "Role: Volunteer",
          ),
          EventCardUi(
              id = "tech-summit",
              title = "Tech Summit Vol.",
              dateLabel = "Fri 20 Jun 2025",
              locationLabel = "Paris, Station F",
              timeLabel = "10:00 – 18:00",
              badge = EventBadge.CONFIRMED,
              footerLabel = "Role: Volunteer",
          ),
          EventCardUi(
              id = "beach-cleanup",
              title = "Beach Clean-up Day",
              dateLabel = "Sun 6 Jul 2025",
              locationLabel = "Marseille, Plage",
              timeLabel = "09:00 – 13:00",
              badge = EventBadge.PENDING_APPROVAL,
              footerLabel = "Awaiting organizer approval",
          ),
          EventCardUi(
              id = "winter-food-bank",
              title = "Winter Food Bank",
              dateLabel = "Sat 18 Jan 2025",
              locationLabel = "Grenoble, Halles",
              timeLabel = "09:00 – 12:00",
              badge = EventBadge.ENDED,
              footerLabel = "Role: Volunteer",
          ),
      )
}
