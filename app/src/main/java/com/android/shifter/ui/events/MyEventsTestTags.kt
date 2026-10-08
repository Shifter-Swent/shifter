// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

object MyEventsTestTags {
  const val STAFF_SCREEN = "staffEventsScreen"
  const val ORGANIZER_SCREEN = "organizerEventsScreen"

  const val TITLE = "myEventsTitle"
  const val SUBTITLE = "myEventsSubtitle"
  const val AVATAR = "myEventsAvatar"
  const val UPCOMING_TAB = "myEventsUpcomingTab"
  const val PAST_TAB = "myEventsPastTab"

  const val EVENT_LIST = "myEventsList"
  const val EMPTY_STATE = "myEventsEmpty"
  const val LOADING = "myEventsLoading"
  const val ERROR = "myEventsError"

  const val ORGANIZER_VIEW_BUTTON = "organizerViewButton"
  const val VOLUNTEER_VIEW_BUTTON = "volunteerViewButton"
  const val SCAN_QR_BUTTON = "scanQrButton"
  const val CREATE_EVENT_BUTTON = "createEventButton"

  fun eventCard(id: String) = "eventCard_$id"

  fun statusPill(id: String) = "eventStatus_$id"

  fun eventAction(id: String) = "eventAction_$id"
}
