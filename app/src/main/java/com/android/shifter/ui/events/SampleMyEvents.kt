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
              footer = EventCardFooter.VOLUNTEER,
          ),
          EventCardUi(
              id = "tech-summit",
              title = "Tech Summit Vol.",
              dateLabel = "Fri 20 Jun 2025",
              locationLabel = "Paris, Station F",
              timeLabel = "10:00 – 18:00",
              badge = EventBadge.CONFIRMED,
              footer = EventCardFooter.VOLUNTEER,
          ),
          EventCardUi(
              id = "beach-cleanup",
              title = "Beach Clean-up Day",
              dateLabel = "Sun 6 Jul 2025",
              locationLabel = "Marseille, Plage",
              timeLabel = "09:00 – 13:00",
              badge = EventBadge.PENDING_APPROVAL,
              footer = EventCardFooter.AWAITING_APPROVAL,
          ),
          EventCardUi(
              id = "winter-food-bank",
              title = "Winter Food Bank",
              dateLabel = "Sat 18 Jan 2025",
              locationLabel = "Grenoble, Halles",
              timeLabel = "09:00 – 12:00",
              badge = EventBadge.ENDED,
              footer = EventCardFooter.VOLUNTEER,
          ),
      )

  val organizer: List<EventCardUi> =
      listOf(
          organized(
              "arts-festival",
              "Community Arts Festival",
              "Sat 28 Jun 2025",
              "Nantes, Île de Nantes",
              "08:00 – 14:00",
              EventBadge.IN_PREPARATION,
          ),
          organized(
              "food-drive",
              "Local Food Drive",
              "Fri 20 Jun 2025",
              "Lille, Grand Place",
              "10:00 – 18:00",
              EventBadge.ONGOING,
          ),
          organized(
              "urban-garden",
              "Urban Garden Workshop",
              "Sun 13 Jul 2025",
              "Rennes, Parc du Thabor",
              "09:00 – 13:00",
              EventBadge.IN_PREPARATION,
          ),
          organized(
              "volunteer-fair",
              "Spring Volunteer Fair",
              "Sat 17 May 2025",
              "Bordeaux, Quays",
              "11:00 – 17:00",
              EventBadge.ENDED,
          ),
          organized(
              "river-cleanup",
              "River Cleanup Day",
              "Sun 4 May 2025",
              "Toulouse, Garonne",
              "09:30 – 13:30",
              EventBadge.ENDED,
          ),
      )

  /**
   * Loaders showing the mock-ups, for tests; the app uses [StaffEventsLoader] and
   * [OrganizerEventsLoader].
   */
  val staffLoader = MyEventsLoader { staff }
  val organizerLoader = MyEventsLoader { organizer }

  /** Temporary withdrawer that always succeeds, until one built on the repositories exists. */
  val withdrawer = EventWithdrawer {}

  private fun organized(
      id: String,
      title: String,
      date: String,
      location: String,
      time: String,
      badge: EventBadge,
  ) = EventCardUi(id, title, date, location, time, badge, footer = EventCardFooter.ORGANIZER)
}
