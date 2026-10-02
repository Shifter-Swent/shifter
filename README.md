# Shifter

## Pitch

Organizations struggle to find volunteers, and on event day they struggle to keep every post covered when people cancel, arrive late or get lost on site.

Shifter matches volunteers to needs by location, like Uber matches drivers to riders. Organizers post open positions, and nearby available volunteers accept them in one tap. On event day, Shifter guides each volunteer to their exact post, checks them in automatically on arrival, and warns managers early when someone will be late. When a post is left empty, Shifter sends the request to the closest available volunteer.

Existing tools rely on static listings and fixed schedules. Shifter reacts to where people are right now, so positions fill faster and stay filled.

Why people install it: coordinators install it so they no longer lose control of their event on the day. Volunteers install it to first find events to volunteer in, and because it tells them exactly where to go and what to do, without a single phone call.

## Features 
Core (GPS)
- Nearby missions, with last-minute positions sent to the closest available volunteers for one-tap acceptance
- Automatic GPS check-in at the volunteer's post (QR fallback) and early lateness alerts
- Live team map, with empty posts filled by the nearest available volunteer

Recruitment & planning
- Missions with requirements, application forms and document uploads (Applied → Accepted / Rejected → Completed)
- Volunteer profile, availability calendar and personal calendar sync
- Automatic scheduling, teams, roles, positions and shifts
- Organizer dashboard

Event support
- Briefings, documents, checklists and emergency contacts
- Direct messages, team chats and announcements
- Ratings after each mission

## Split-app model

Events, posts and their zones, shifts, assignments, applications, check-ins, live on-shift locations and announcements are stored in Firebase, which synchronizes positions and coverage status in real time between volunteers, managers and organizers.

The device keeps a local copy of the user's own data: their schedule, post locations and zones, the event map, the briefing and emergency contacts. GPS processing happens on the device, which detects arrival in a post's zone, so check-in does not depend on the server. Check-ins and location updates are stored locally and synchronized with Firebase whenever a connection is available.

## Multi-user support

Organizer

Creates the event, places posts on the map and defines their check-in zones, publishes missions, accepts volunteers, assigns them to posts and shifts, appoints managers, and has a live overview of coverage across the whole event.

Manager

A per-event role an organizer gives to an experienced volunteer. A manager is responsible for one sector of the event. They see their team's locations and post coverage on a live map, receive lateness and gap alerts, and confirm reassignments suggested by the app.

Volunteer

Applies to missions, sees their assigned post and shift, is guided to the post, is checked in automatically, and receives reassignments and announcements.

All users sign in with the same account. They start as volunteers, can be appointed manager for a given event, and can switch to Organizer mode from their profile to run their own events.°

Authentication:
- Users create and access their account using Google Sign-In.

## Sensor use

GPS is the core of Shifter. It drives four behaviours that the app cannot offer without it:

Arrival detection. Each post has a geographic zone. When a volunteer's position enters their post's zone within the check-in window, they are checked in automatically. No action is needed from them or from a manager.
Lateness prediction. Before a shift starts, Shifter compares each volunteer's current position and estimated travel time with the shift's start time. A volunteer who is too far away is flagged as at risk, and their manager is alerted before the post goes uncovered.
Live coverage. Managers see on-shift volunteers and each post's status (covered, at risk, empty) on a map. A volunteer who leaves their zone during a shift turns the post to "at risk".
Nearest-volunteer reassignment. When a gap appears, Shifter ranks available volunteers by distance to the empty post. The manager reassigns one with a single tap, and that volunteer is guided there.

Privacy and battery. Location is shared only during a volunteer's active shift, plus a short window before it starts. It is visible only to that volunteer's own manager and to the event's organizers. Volunteers can always see when sharing is on. Outside shifts, the app does not track location.

Camera. A QR code scanned by the manager serves as a fallback check-in where GPS is unreliable, such as indoors or in very dense crowds.

## Offline mode

Mobile networks are often saturated at large events, so the volunteer side of the core flow keeps working without a connection.

Offline, volunteers keep access to:

Their schedule and assigned post
The event map with their post's location
The briefing and emergency contacts
Automatic check-in: GPS works without Internet, and arrival in the zone is detected on the device and stored locally
Their QR code, as a fallback

Check-ins and location updates are queued on the device and synchronized with Firebase automatically when the connection returns. Managers keep their last known view of the team map. Posts whose data is out of date are marked as such, so a manager can tell a confirmed gap from missing information. Lateness alerts and reassignments resume as soon as the manager or the volunteer is back online.

## Figma
Shifter Figma's model : https://www.figma.com/make/a5LSzNh9e0em1xIsjy1nIF/Shifter
