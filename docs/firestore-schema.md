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
| `events/{eventId}/channels/general/messages/{messageId}` | A message of the event's general chat channel | `ChatMessageSchema` |
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

### `events/{eventId}/teams/{teamId}/messages/{messageId}`

The team's chat channel during the event. The document id is generated by Firestore. A message is
final: it is never edited or deleted.

| Field | Type | Notes |
|---|---|---|
| `senderId` | string | uid of the sender, always the signed-in user. Only the uid is stored: names are read from the public profiles, so a renamed user never shows up under an old name |
| `text` | string | Trimmed, 1 to 1000 characters |
| `sentAt` | timestamp | Server timestamp, equal to `request.time` |
| `announcement` | boolean | `true` for an announcement, which only the team's manager can send: not its members, nor the organizer, and a team with no manager has none. In the general channel, only the event's organizer can send one. Absent on the messages sent before announcements existed, which are read as normal messages. Never changed, like the rest of the message |

The channel is read with `orderBy(sentAt)` and `limitToLast(100)`, which only needs Firestore's
automatic single-field index. A message sent offline is shown at once, before the server has set
`sentAt`: it is read with an estimate of the server time (`ServerTimestampBehavior.ESTIMATE`)
instead of being rejected as malformed. Firestore sorts it last until the server has it, then by the
time the server received it.

### `events/{eventId}/channels/general/messages/{messageId}`

The event's general channel, where its organizer and all its participants talk, the managers of its
teams included. Its messages have the same fields as a team's message above, are read the same way
and are final too. Only the event's organizer sends announcements there. `channels/general` is only a path: no document is stored there. The event's
other channels, such as an admin channel, would sit next to it in `channels`.

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
| `completed` | boolean | `true` once closed by hand before its end, which cannot be undone (checked by the rules). A mission past its `endAt` is completed whatever this says: that is computed, never written |

Two GeoTimed missions of the same team must not overlap; adjacent slots are allowed. The app checks
this before writing (`FirestoreMissionRepository.createMission` and `updateMission`), not the
rules, and the check is not atomic. `updateMission` only writes `title`, `description`,
`volunteersNeeded`, `startAt`, `endAt` and `location`. It refuses a `volunteersNeeded` lower than
the number of `assigneeIds`, and keeps the check-ins.

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
| `teams/{teamId}/liveLocations/{uid}` | `get`: the volunteer themself, the manager of that team and the event's organizer. `list`: the manager and the organizer only, checked on the path so the whole collection can be queried. Both only within the event's period (see below) | Create and update by the volunteer themself (`uid` is the document id), only within the event's period, while they are in the team's `memberIds` and their `users/{uid}.locationSharingEnabled` is `true`, and only with a valid `position` and `measuredAt`. Delete always allowed to the volunteer, so they can clean up after turning sharing off, leaving the team, or the event ending or being deleted, and to the event's organizer, who is the one who takes a volunteer out of a team (see below). Not to the team's manager, who writes neither `memberIds` nor `managerId` |
| `teams/{teamId}/messages/{messageId}` | The team's members (`memberIds`), its manager (`managerId`) and the event's organizer, checked on the team as it is now, so someone removed from the team or who withdraws loses access at once. Checked on the path, so the whole channel can be queried | Create only, by the same users, with only `senderId` equal to the signed-in user, `sentAt` equal to the request time and a trimmed `text` of 1 to 1000 characters, and an optional boolean `announcement`, `true` only from the team's manager. No update, no delete |
| `channels/general/messages/{messageId}` | The event's organizer and its participants (`participantIds`), checked on the participants as they are now, so someone who withdraws or is rejected loses access at once. A team's manager is always a participant, since they are appointed among its members. Checked on the path, so the whole channel can be queried | Create only, by the same users, with the same fields as a team's message, an announcement only from the organizer. No update, no delete |
| `missions/{missionId}/checkIns/{uid}` | The volunteer, the manager of the mission's team, or the organizer. Checked on the path, so a missing check-in can be read | Create only, for a uid in the mission's `assigneeIds` who is still a participant of the event (a volunteer who withdrew or was rejected stays assigned until unassigned), with `userId` equal to the document id, `checkedInBy` equal to the signed-in user and `checkedInAt` equal to the request time. `SELF` by the volunteer themself; `MANAGER` by the manager of the mission's team or the organizer (only the organizer for a mission of no team). No update, no delete |

A collection-group rule (`/{path=**}/membershipRequests/{uid}`) lets a volunteer list their own
requests across all events.

Likewise, `/{path=**}/missions/{missionId}` lets a volunteer list the missions whose `assigneeIds`
contain them, across all events, and nobody else's.

`/{path=**}/checkIns/{uid}` lets a volunteer list their own check-ins across all missions, filtered
by `userId`.

Collection-group queries need a collection-group index on the filtered field, declared in
`firestore.indexes.json` (`membershipRequests.userId`, `missions.assigneeIds`, `checkIns.userId`).
The emulator does not check indexes, so a missing one only fails in production. The "Deploy
Firestore" workflow deploys `firestore.rules` and `firestore.indexes.json` on every push to `main`
that changes them; it can also be run by hand from the Actions tab.

A withdrawal is the one exception to the table above: a volunteer may delete their own request
and, in the same commit, remove themself (and only themself) from `eventParticipants` and from their
team: from its `memberIds`, or, for its manager, from `managerId`, which becomes `null`. A request
can only be deleted once its volunteer is no longer a participant, and never once it is
`REJECTED`, so a volunteer cannot undo a rejection by applying again.

## Known gaps

- **The rules do not validate the fields of `teams`, `shifts` and `missions`**, only who may
  write them, and which assignees a manager adds; malformed documents are caught by the mappers.
- **A volunteer who left can still read their missions and check-ins.** They stay in
  `assigneeIds` until unassigned, and the rules that let a volunteer list their own missions and
  check-ins do not check participation; #337 is to address it.
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
  orphans can be cleaned up: the organizer's delete is not an exception here, because it is granted
  by reading the event's `organizerId`, which a deleted event no longer has.
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

  - **Sharing turned off:** done in Settings (#404). `UserRepository.setLocationSharing` writes
    the setting and deletes the volunteer's position in each of their teams **in one batch**, so
    the server receives both at once and the manager stops seeing the position at that moment.
    The teams are those of every event the volunteer was accepted to.

    What the manager still sees while the change has not reached the server:

    - **Offline:** the batch is saved on the device and sent as a whole once it reconnects, also
      after the app was closed. Until then the manager sees the **last position this device shared
      before going offline**, and no newer one from it: a device sends its queued writes in order,
      so a position it queued after the change arrives after the setting and is refused. Settings
      tells the volunteer so.
    - **Another device of the same user:** the rules check the consent stored on the server, so a
      device that is online keeps publishing until the server receives the change.
    - **A team the device never loaded:** offline, the teams come from the cache, so a team never
      opened on this device is missed. The publisher removes its position at its next session in
      that team, since it removes it whenever the consent is off (#377). Sessions only run while
      the event is ongoing: if none comes before the end, the position stays stored but nobody can
      read it once the event's period is over.
    - **Uninstalled, or never reconnected:** the batch never leaves the device, and the position
      stays readable until the event's period ends.
  - **Leaving the team:** done. Every path that takes a volunteer out of a team's `memberIds`
    deletes their position in the same commit, so the manager stops seeing it at once:

    | Path | Who writes it | Atomic because |
    |---|---|---|
    | `MembershipRequestRepository.withdraw` | the volunteer | one transaction, with the request and `memberIds` |
    | `MembershipRequestRepository.reject` of an accepted volunteer | the organizer | the same transaction as the status and `memberIds` |
    | `TeamRepository.removeMember` | the organizer | one batch, with the `memberIds` update |
    | `TeamRepository.setManager`, appointing a member | the organizer | the same transaction that rewrites `memberIds` |

    `delete` is therefore allowed to the volunteer **and** to the event's organizer: a removal the
    organizer makes cannot wait for the volunteer's device, which may be offline, killed or
    uninstalled. The manager is deliberately left out, since they write neither `memberIds` nor
    `managerId` and so never remove anyone. A batch or a transaction commits whole, so a membership
    change that is refused deletes nothing either.

    What this is **not** is a guarantee that a stored position always matches `memberIds`. Rules
    *can* inspect the state a commit will leave behind, with `getAfter()` and `existsAfter()`:
    `membershipRequests` already uses them to require a decision to agree with `memberIds` after
    the same commit (`isTeamMemberAfter`). What no rule can do is the converse - require a write to
    `memberIds` to be accompanied by the delete - and the delete here is not conditioned on the
    after state either. Two gaps follow:

    - **Not enforced, and not enforceable by rules:** an organizer writing `memberIds` directly,
      rather than through these repositories, removes a volunteer without deleting their position.
      It then stays readable until the volunteer's app deletes it or the event's period ends,
      exactly as before. This is the one that matters, and only a backend mechanism closes it.
    - **Not enforced, but enforceable:** the permission lets the organizer delete the position of a
      volunteer who is still in the team, because it does not check `memberIds` after the commit.
      Adding that condition would scope it to the cleanup; it only loses a position the
      volunteer's app replaces on its next update, so it is left out for now.

    Both are bounded by the event's period, like every read here. Closing the first one properly
    needs the backend mechanism the consent gap needs too (see above), which no PR of #305
    plans.

  A collection-group query over `liveLocations` is not an option for finding a volunteer's
  positions to delete: there is no `match /{path=**}/liveLocations/{uid}` rule, so the query is
  denied however the documents are shaped, and adding a `userId` field would not change that.
- **Offline:** with Firestore's persistent cache, a query made offline returns cached data, which
  can be empty or stale, rather than failing. Transactions need a connection.
