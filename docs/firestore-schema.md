<!-- Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com> -->

# Firestore schema

The single reference for how Shifter stores its data in Cloud Firestore. It replaces the
discussion in #103. When you change a collection, a field or a rule, update this file in the same
PR.

The source of truth stays the code: each entity has a `*Schema` object holding its collection and
field names, next to its mapper in `model/<entity>/`. This page explains how they fit together.

## Overview

```
users/{uid}
events/{eventId}
  ├── teams/{teamId}
  ├── shifts/{shiftId}
  ├── missions/{missionId}
  └── membershipRequests/{uid}
eventParticipants/{eventId}
```

| Path | What it holds | Schema object |
|---|---|---|
| `users/{uid}` | A user's profile, keyed by their Firebase Auth uid | `UserRepositoryFirestore` |
| `events/{eventId}` | An event created by an organizer | `EventSchema` |
| `events/{eventId}/teams/{teamId}` | A team of the event | `TeamSchema` |
| `events/{eventId}/shifts/{shiftId}` | A time slot a team has to staff | `ShiftSchema` |
| `events/{eventId}/missions/{missionId}` | A task with its own time slot | `MissionSchema` |
| `events/{eventId}/membershipRequests/{uid}` | A volunteer's request to join, keyed by their uid | `MembershipRequestSchema` |
| `eventParticipants/{eventId}` | The accepted volunteers of an event | written by `MembershipRequestRepositoryFirestore` |

## Conventions

- **Ids come from the path, never from fields.** A document's own id and its parent event's id are
  read from the document path. Storing them as fields would create a second source of truth that
  could contradict the path.
- **References are ids only.** A manager, member or assignee is stored as a `users/{uid}` id. No
  profile data (name, email) is copied, because it would go stale.
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
| `createdAt` | timestamp | Set to the server time on creation |

### `events/{eventId}`

| Field | Type | Notes |
|---|---|---|
| `organizerId` | string | uid of the organizer, cannot change after creation |
| `title` | string | |
| `description` | string | |
| `type` | string | `MUSIC`, `MARKET`, `NATURE`, `FOOD`, `SPORT`, `PARTY`, `OTHER`. An unknown value reads as `OTHER` |
| `imageUrl` | string \| null | |
| `startAt`, `endAt` | timestamp | |
| `location` | map | `address` (string), `latitude` and `longitude` (number \| null, both set or both null) |
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
| `checkInZone` | map \| null | `latitude`, `longitude`, `radiusMeters`, all required when the zone is set |
| `createdAt` | timestamp | |

Belonging to a team (`memberIds`) is not the same as working a given slot (`Shift.assigneeIds`).

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
| `location` | map \| null | `address` (non-blank string), `latitude` and `longitude` (number, both required). Absent or null: no location. A mission with one is "GeoTimed" |
| `assigneeIds` | list of string | Queried with `array-contains` across all events to list a volunteer's missions |
| `createdAt` | timestamp | |

Two GeoTimed missions of the same team must not overlap; adjacent slots are allowed. The app checks
this before writing (`FirestoreMissionRepository.createMission`), not the rules, and the check is
not atomic.

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
| `users/{uid}` | The user | Create and update by the user. Only `displayName` and `locationSharingEnabled` can change. No delete |
| `events/{eventId}` | Any signed-in user | Create with yourself as `organizerId`. Update and delete by the organizer only |
| `membershipRequests/{uid}` | The volunteer, or the event's organizer | Created `PENDING` by the volunteer, for a `teamId` that exists in the event. Only the organizer updates it, and only `status`: `ACCEPTED` needs the volunteer in the team's `memberIds` after the same commit, `REJECTED` needs them out of it. No delete |
| `eventParticipants/{eventId}` | The event's organizer. A volunteer reads their own request instead | The organizer only: at most one uid added or removed per write. An added uid needs an `ACCEPTED` request, a removed one a `REJECTED` request, in the same commit |
| `teams`, `shifts`, `missions` | The organizer and the event's participants | The organizer only, except a volunteer leaving their team when they withdraw (see below; temporary, until #208). Nothing is allowed under an event that does not exist |

A collection-group rule (`/{path=**}/membershipRequests/{uid}`) lets a volunteer list their own
requests across all events.

Likewise, `/{path=**}/missions/{missionId}` lets a volunteer list the missions whose `assigneeIds`
contain them, across all events, and nobody else's.

Collection-group queries need a collection-group index on the filtered field, declared in
`firestore.indexes.json` (`membershipRequests.userId`, `missions.assigneeIds`). The emulator does
not check indexes, so after changing that file, deploy it with
`firebase deploy --only firestore:indexes`.

A withdrawal is the one exception to the table above: a volunteer may delete their own request
and, in the same commit, remove themself (and only themself) from `eventParticipants` and from their
team. A request
can only be deleted once its volunteer is no longer a participant, and never once it is
`REJECTED`, so a volunteer cannot undo a rejection by applying again.

## Known gaps

- **The rules do not validate the fields of `teams`, `shifts` and `missions`**, only who may
  write them; malformed documents are caught by the mappers. Team managers have no write access
  yet: only the organizer edits these subcollections.
- **Deleting an event does not delete its subcollections.** Whoever implements event deletion has
  to remove them explicitly.
- **`User.createdAt`** is still read as a `java.util.Date`, unlike the other models, which use
  `Instant`.
- **Offline:** with Firestore's persistent cache, a query made offline returns cached data, which
  can be empty or stale, rather than failing. Transactions need a connection.
