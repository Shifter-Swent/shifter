<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->
<!-- Co-authored-by: Claude Opus 5 <noreply@anthropic.com> -->

# Firestore schema

The single reference for how Shifter stores its data in Cloud Firestore. It replaces the
discussion in #103. When you change a collection, a field or a rule, update this file in the same
PR.

The source of truth stays the code: each entity has a `*Schema` object holding its collection and
field names, next to its mapper in `model/<entity>/`. This page explains how they fit together.

## Overview

```
users/{uid}
publicProfiles/{uid}
events/{eventId}
  ├── teams/{teamId}
  │     └── liveLocations/{uid}
  ├── shifts/{shiftId}
  ├── missions/{missionId}
  │     └── checkIns/{uid}
  └── membershipRequests/{uid}
eventParticipants/{eventId}
```

| Path | What it holds | Schema object |
|---|---|---|
| `users/{uid}` | A user's private profile, keyed by their Firebase Auth uid | `UserRepositoryFirestore` |
| `publicProfiles/{uid}` | What any signed-in user may see of a user | `UserRepositoryFirestore` |
| `events/{eventId}` | An event created by an organizer | `EventSchema` |
| `events/{eventId}/teams/{teamId}` | A team of the event | `TeamSchema` |
| `events/{eventId}/teams/{teamId}/liveLocations/{uid}` | Where a volunteer of the team is, keyed by their uid | `LiveLocationSchema` |
| `events/{eventId}/shifts/{shiftId}` | A time slot a team has to staff | `ShiftSchema` |
| `events/{eventId}/missions/{missionId}` | A task with its own time slot | `MissionSchema` |
| `events/{eventId}/missions/{missionId}/checkIns/{uid}` | A volunteer's presence at a mission, keyed by their uid | `CheckInSchema` |
| `events/{eventId}/membershipRequests/{uid}` | A volunteer's request to join, keyed by their uid | `MembershipRequestSchema` |
| `eventParticipants/{eventId}` | The accepted volunteers of an event | written by `MembershipRequestRepositoryFirestore` |

## Conventions

- **Ids come from the path, never from fields.** A document's own id and its parent event's id are
  read from the document path. Storing them as fields would create a second source of truth that
  could contradict the path.
- **References are ids only.** A manager, member or assignee is stored as a `users/{uid}` id. No
  profile data (name, email) is copied, because it would go stale. To show another user, read their
  `publicProfiles/{uid}`.
- **Instants are Firestore `Timestamp`s** with microsecond precision. Time slots are named
  `startAt` / `endAt` everywhere.
- **Enums are stored by name** (`"PENDING"`, `"MUSIC"`…).
- **Lists of ids** (`memberIds`, `assigneeIds`…): an absent field reads as an empty list. An
  explicit `null` or a non-string entry is rejected.
- **Malformed documents fail loudly.** A mapper throws `IllegalStateException` naming the entity,
  the document and the field, rather than returning `null` or a default. The shared readers live in
  `model/firestore/FirestoreMapperUtils.kt`; new mappers should use them.
- **Naming:** `teams` / `teamId` (not poles or sectors), `membershipRequests` (not applications),
  `startAt` / `endAt` (not `startsAt`).

## Collections

### `users/{uid}`

| Field | Type | Notes |
|---|---|---|
| `displayName` | string | |
| `email` | string | |
| `locationSharingEnabled` | bool | Opt-in, always `false` at creation |
| `createdAt` | timestamp | Set to the server time on creation. Read as `null` while the write is pending |
| `mode` | string | `UserMode` by name: `"VOLUNTEER"` or `"ORGANIZER"`. Saved at sign-in from the screen used, when the profile has none; changed in Settings. Absent on profiles from before modes. A value the app does not know is read as no mode |

Only its owner reads it: what others may see of a user lives in their public profile.

### `publicProfiles/{uid}`

Created on sign-in, in the same transaction as `users/{uid}`, and on the next sign-in for a profile
created before public profiles existed. It never holds sensitive data: an email or a phone number
stays in `users/{uid}`.

| Field | Type | Notes |
|---|---|---|
| `displayName` | string | Always the one of `users/{uid}` (checked by the rules) |

### `events/{eventId}`

| Field | Type | Notes |
|---|---|---|
| `organizerId` | string | uid of the organizer, cannot change after creation |
| `title` | string | |
| `description` | string | |
| `type` | string | `MUSIC`, `MARKET`, `NATURE`, `FOOD`, `SPORT`, `PARTY`, `OTHER`. An unknown value reads as `OTHER` |
| `imageUrl` | string \| null | |
| `startAt`, `endAt` | timestamp | |
| `location` | map | `address` (string), `latitude` (-90 to 90) and `longitude` (-180 to 180) (number \| null, both set or both null) |
| `emergencyContacts` | list of maps | `name`, `phoneNumber` (string), `role` (string \| null) |
| `joinCode` | string | Unique, generated on creation |
| `status` | string | `PREPARATION`, `ONGOING`, `COMPLETED`, `ARCHIVED` |
| `createdAt` | timestamp | |

### `events/{eventId}/teams/{teamId}`

| Field | Type | Notes |
|---|---|---|
| `name` | string | |
| `description` | string | What the team does, written when it is created |
| `icon` | string | Icon identifier |
| `managerId` | string \| null | `null` until the organizer appoints a manager |
| `memberIds` | list of string | The volunteers of the team. **Never contains the manager**. A volunteer belongs to at most one team per event: the one their accepted request names |
| `volunteersNeeded` | number \| null | Whole number. `null` until the staffing need is set: it is not asked for when the team is created |
| `createdAt` | timestamp | |

Belonging to a team (`memberIds`) is not the same as working a given slot (`Shift.assigneeIds`).

### `events/{eventId}/teams/{teamId}/liveLocations/{uid}`

The last position a volunteer shared with their manager, while their `locationSharingEnabled` is
on and the event is running. The document id is their uid, so a volunteer shares one position per
team, and overwriting it is the only way to update it.

| Field | Type | Notes |
|---|---|---|
| `position` | map | `latitude` (number, -90 to 90) and `longitude` (number, -180 to 180), both required and nothing else |
| `measuredAt` | timestamp | When the GPS measured `position`, **not** when the write reached the server. At most 5 min after `request.time`, and never older than the measure it replaces |

`measuredAt` is the device's time on purpose: a position measured offline keeps the time it was
measured at when it is finally sent, so a reader can tell how old it is. The past is therefore
open, and how old a position may be before it is ignored is the reader's call. The rules bound the
field on two sides all the same, because the reader's call is made on this very field:

- **Not in the future**, beyond 5 min of tolerance for a device clock that runs ahead. A measure
  dated 2099 would otherwise be considered fresh for ever, pinning the volunteer at a wrong place
  with no time limit, and a wrong clock is enough for that.
- **Not older than the measure it replaces.** With two devices, a replayed write or offline writes
  sent out of order, the position the manager sees would otherwise jump back to where the volunteer
  was earlier. The same measure sent twice is still accepted, so a retry is not an error.

Both are refused as a permission denial, which the backend does not tell apart from a revoked
consent. The two bounds can also work against each other: a clock 4 min fast is within the 5 min
tolerance, so it stores a position dated in the future, and once the clock is corrected the
device's own later measures look older than the stored one and are refused until it is caught up
with. A rejected write means the attempted measurement was not stored, but does not necessarily
indicate revoked consent. A publisher must not delete an existing position solely because of such
a denial; see `LiveLocationRepositoryException.PermissionDenied`.

### `events/{eventId}/shifts/{shiftId}`

| Field | Type | Notes |
|---|---|---|
| `teamId` | string | A team of the same event |
| `assigneeIds` | list of string | The volunteers scheduled for this slot |
| `startAt`, `endAt` | timestamp | |
| `createdAt` | timestamp | |

### `events/{eventId}/missions/{missionId}`

| Field | Type | Notes |
|---|---|---|
| `title` | string | |
| `description` | string | |
| `teamId` | string \| null | `null` means "General": the mission belongs to no team |
| `volunteersNeeded` | number | Whole number, at least 1 (checked by the app) |
| `startAt`, `endAt` | timestamp | A mission has its own slot, with no `shiftId`: it may cover only part of a shift |
| `location` | map \| null | `address` (non-blank string), `latitude` (-90 to 90) and `longitude` (-180 to 180) (number, both required). Absent or null: no location. A mission with one is "GeoTimed" |
| `assigneeIds` | list of string | Queried with `array-contains` across all events to list a volunteer's missions |
| `createdAt` | timestamp | |

Two GeoTimed missions of the same team must not overlap; adjacent slots are allowed. The app checks
this before writing (`FirestoreMissionRepository.createMission`), not the rules, and the check is
not atomic.

### `events/{eventId}/missions/{missionId}/checkIns/{uid}`

The proof that a volunteer was present at a mission. The document id is the volunteer's uid, so a
volunteer is checked in at most once per mission. A check-in is never changed or deleted.

| Field | Type | Notes |
|---|---|---|
| `userId` | string | Same as the document id. Stored so a volunteer's check-ins can be queried across all missions (a collection-group query cannot filter on the document id alone) |
| `method` | string | `SELF` (the volunteer's own app) or `MANAGER` (recorded by the team's manager or the organizer) |
| `checkedInAt` | timestamp | Server timestamp, equal to the request time |
| `checkedInBy` | string | uid of who recorded it: the volunteer for `SELF`, the manager or organizer for `MANAGER` |

The rules check neither the mission's time slot nor its location. The request time is when the write
reaches the server: a `SELF` check-in made offline during the slot can arrive after `endAt`, and it
must be accepted. Any other time or position would come from the client, so it could be forged.
The app checks both instead: the eligibility logic (#164) says whether the volunteer is in the slot
and the zone, and the automatic check-in (#168) only records a `SELF` check-in when they are.

`FirestoreCheckInRepository` reads and writes these documents:

- **A second check-in** is refused by the rules with `PERMISSION_DENIED`, like any forbidden
  write. The repository then reads the document from the server: if it exists, the error is
  `AlreadyCheckedIn`, otherwise `NotAllowed`.
- **`checkedInAt` of a pending write** (offline, or not yet acknowledged) is read with an estimate
  of the server time (`ServerTimestampBehavior.ESTIMATE`), so a pending check-in is listed
  straight away. `checkIn` itself only returns once the server has accepted the write.
- **A volunteer's check-ins of one event:** the collection-group query on `userId` returns their
  check-ins of every event. The repository then keeps those of the requested event, reading the
  event id from each document's path (`events/{eventId}/…`), since the document has no `eventId`
  field.

### `events/{eventId}/membershipRequests/{uid}`

The document id is the volunteer's uid, so a volunteer can send only one request per event.

| Field | Type | Notes |
|---|---|---|
| `userId` | string | Same as the document id |
| `teamId` | string | The team of the same event the volunteer applies to. Cannot change once sent |
| `availability` | list of maps | `startAt`, `endAt` (timestamp); at least one slot |
| `status` | string | `PENDING`, then `ACCEPTED` or `REJECTED` |
| `createdAt` | timestamp | |

### `eventParticipants/{eventId}`

| Field | Type | Notes |
|---|---|---|
| `participantIds` | list of string | uids of the volunteers whose request is `ACCEPTED` |

Accepting or rejecting a request updates the request, this document and the `memberIds` of the
requested team in the same batch. The rules check that they agree (see below). A volunteer who
withdraws deletes their request and leaves this document and their team in the same transaction, so
they can apply again later.

## Security rules

Defined in `firestore.rules` and loaded by the emulator through `firebase.json`.

Rules **do not cascade** into subcollections: every subcollection needs its own `match` block, or
every access to it is denied.

| Path | Read | Write |
|---|---|---|
| `users/{uid}` | The user | Create and update by the user. Only `displayName` and `locationSharingEnabled` can change; a new `displayName` must be written to the public profile in the same commit. No delete |
| `publicProfiles/{uid}` | Any signed-in user, one document at a time (`get`): listing the collection is denied | Create and update by the user, with only `displayName`, equal to the one of `users/{uid}` after the same commit. No delete |
| `events/{eventId}` | Any signed-in user | Create with yourself as `organizerId`. Update and delete by the organizer only |
| `membershipRequests/{uid}` | The volunteer, or the event's organizer | Created `PENDING` by the volunteer, for a `teamId` that exists in the event. Only the organizer updates it, and only `status`: `ACCEPTED` needs the volunteer in the team's `memberIds` after the same commit, `REJECTED` needs them out of it. No delete |
| `eventParticipants/{eventId}` | The event's organizer. A volunteer reads their own request instead | The organizer only: at most one uid added or removed per write. An added uid needs an `ACCEPTED` request, a removed one a `REJECTED` request, in the same commit |
| `teams` | Any signed-in user, since a volunteer picks their team before applying | The organizer only, never with `managerId` among the `memberIds`, except a volunteer leaving their team when they withdraw (see below). Nothing is written under an event that does not exist |
| `shifts` | The organizer and the event's participants | The organizer only. Nothing is allowed under an event that does not exist |
| `missions` | The organizer and the event's participants | The organizer, or the manager of the mission's team (`teamId`), for the missions of that team only, before and after the write. A manager only adds members of the team to `assigneeIds`, each once, and never more than `volunteersNeeded`; assignees already on the mission may stay. Only the organizer writes a mission of no team. Nothing is allowed under an event that does not exist |
| `teams/{teamId}/liveLocations/{uid}` | `get`: the volunteer themself, the manager of that team and the event's organizer. `list`: the manager and the organizer only, checked on the path so the whole collection can be queried. Both only within the event's period (see below) | Create and update by the volunteer themself (`uid` is the document id), only within the event's period, while they are in the team's `memberIds` and their `users/{uid}.locationSharingEnabled` is `true`, and only with a valid `position` and `measuredAt`. Delete always allowed to the volunteer, so they can clean up after turning sharing off, leaving the team, or the event ending or being deleted |
| `missions/{missionId}/checkIns/{uid}` | The volunteer, the manager of the mission's team, or the organizer. Checked on the path, so a missing check-in can be read | Create only, for a uid in the mission's `assigneeIds`, with `userId` equal to the document id, `checkedInBy` equal to the signed-in user and `checkedInAt` equal to the request time. `SELF` by the volunteer themself; `MANAGER` by the manager of the mission's team or the organizer (only the organizer for a mission of no team). No update, no delete |

A collection-group rule (`/{path=**}/membershipRequests/{uid}`) lets a volunteer list their own
requests across all events.

Likewise, `/{path=**}/missions/{missionId}` lets a volunteer list the missions whose `assigneeIds`
contain them, across all events, and nobody else's.

`/{path=**}/checkIns/{uid}` lets a volunteer list their own check-ins across all missions, filtered
by `userId`.

Collection-group queries need a collection-group index on the filtered field, declared in
`firestore.indexes.json` (`membershipRequests.userId`, `missions.assigneeIds`, `checkIns.userId`).
The emulator does not check indexes, so after changing that file, deploy it with
`firebase deploy --only firestore:indexes`.

A withdrawal is the one exception to the table above: a volunteer may delete their own request
and, in the same commit, remove themself (and only themself) from `eventParticipants` and from their
team: from its `memberIds`, or, for its manager, from `managerId`, which becomes `null`. A request
can only be deleted once its volunteer is no longer a participant, and never once it is
`REJECTED`, so a volunteer cannot undo a rejection by applying again.

## Known gaps

- **The rules do not validate the fields of `teams`, `shifts` and `missions`**, only who may
  write them, and which assignees a manager adds; malformed documents are caught by the mappers.
- **Check-ins trust `assigneeIds` only.** A volunteer who withdrew or was rejected stays assigned
  until the organizer unassigns them, so they can still check themself in; #313 is to also
  require them to be a participant.
- **Any signed-in user reads an event's teams, `memberIds` included.** The members are uids
  only, but public profiles turn them into names. Events, join codes included, are listable by
  anyone signed in too; #160 is to address both, e.g. with a public subcollection holding only
  what candidates need.
- **`displayName` is in both `users/{uid}` and `publicProfiles/{uid}`.** The rules keep them equal,
  so the copy cannot go stale; #234 proposes to keep it in the public profile only.
- **Deleting an event does not delete its subcollections.** Whoever implements event deletion has
  to remove them explicitly. `liveLocations` is the one place the rules already account for it:
  every read and write under a deleted event is denied, since a team's manager would otherwise go
  on reading the positions orphaned under it. Only the volunteer's own delete still works, so the
  orphans can be cleaned up.
- **A shared position outlives the consent, until the event's period ends.** Listing
  `liveLocations` is allowed by the path alone, because a condition on each document would make the
  manager's query of the collection fail as soon as one volunteer no longer satisfied it (rules are
  not query filters). Turning `locationSharingEnabled` off, or leaving the team, therefore stops
  the updates at once but does not hide the last position, and neither does the rest of the rules:
  `memberIds` and the consent are read with the document's `uid`, so requiring either of them for
  `list` would break the manager's query instead of hiding one document.

  What bounds the exposure is the event's period: once `endAt` plus the hour of margin has passed,
  nobody reads the position any more, whatever happened to the consent or the membership. Inside
  the period, only a delete hides it, and only the volunteer themself may delete it:

  - **Sharing turned off:** the publishing PR has to delete the position in the same step as the
    setting. That covers the app's own path, not an app that is offline, killed or uninstalled at
    that moment: the position then stays readable to the manager until the volunteer's app runs
    again or the event's period ends. Closing that gap needs a backend mechanism (a scheduled
    function deleting positions whose owner has sharing off, or a server-written timestamp), which
    no PR of #305 plans.
  - **Leaving the team:** a volunteer's own withdrawal can delete it in the same commit as leaving
    `memberIds`, which the rules already allow. A removal by the organizer cannot: `delete`
    requires `request.auth.uid == uid`, so nobody but the volunteer may take their position out,
    and that case is bounded only by the event's period. Granting the manager and the organizer a
    delete is the smallest fix, and belongs to the PR that implements the cleanup.

  A collection-group query over `liveLocations` is not an option for finding a volunteer's
  positions to delete: there is no `match /{path=**}/liveLocations/{uid}` rule, so the query is
  denied however the documents are shaped, and adding a `userId` field would not change that.
- **Offline:** with Firestore's persistent cache, a query made offline returns cached data, which
  can be empty or stale, rather than failing. Transactions need a connection.
