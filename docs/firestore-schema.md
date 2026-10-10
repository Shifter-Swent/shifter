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
| `events/{eventId}/teams/{teamId}` | A team ("pôle" in the app) of the event | `TeamSchema` |
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
| `memberIds` | list of string | **Deprecated**: participants live in `eventParticipants`. To be removed (#103) |
| `joinCode` | string | Unique, generated on creation |
| `status` | string | `PREPARATION`, `ONGOING`, `COMPLETED`, `ARCHIVED` |
| `createdAt` | timestamp | |

### `events/{eventId}/teams/{teamId}`

| Field | Type | Notes |
|---|---|---|
| `name` | string | |
| `icon` | string | Icon identifier |
| `managerId` | string \| null | `null` until the organizer appoints a manager |
| `memberIds` | list of string | The volunteers of the team. **Never contains the manager**. A volunteer may be in several teams |
| `volunteersNeeded` | number | Whole number |
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
| `assigneeIds` | list of string | |
| `createdAt` | timestamp | |

### `events/{eventId}/membershipRequests/{uid}`

The document id is the volunteer's uid, so a volunteer can send only one request per event.

| Field | Type | Notes |
|---|---|---|
| `userId` | string | Same as the document id |
| `preferredTeamIds` | list of string | Teams the volunteer would like to join |
| `availability` | list of maps | `startAt`, `endAt` (timestamp); at least one slot |
| `status` | string | `PENDING`, then `ACCEPTED` or `REJECTED` |
| `createdAt` | timestamp | |

### `eventParticipants/{eventId}`

| Field | Type | Notes |
|---|---|---|
| `participantIds` | list of string | uids of the volunteers whose request is `ACCEPTED` |

Accepting or rejecting a request updates the request and this document in the same batch. The rules
check that they agree (see below). A volunteer who withdraws deletes their request and leaves this
document in the same transaction, so they can apply again later.

## Security rules

Defined in `firestore.rules` and loaded by the emulator through `firebase.json`.

Rules **do not cascade** into subcollections: every subcollection needs its own `match` block, or
every access to it is denied.

| Path | Read | Write |
|---|---|---|
| `users/{uid}` | The user | Create and update by the user. Only `displayName` and `locationSharingEnabled` can change. No delete |
| `events/{eventId}` | Any signed-in user | Create with yourself as `organizerId`. Update and delete by the organizer only |
| `membershipRequests/{uid}` | The volunteer, or the event's organizer | Created `PENDING` by the volunteer. Only the organizer updates it, and only `status`. No delete |
| `eventParticipants/{eventId}` | Any signed-in user | The organizer only: at most one uid added or removed per write. An added uid needs an `ACCEPTED` request, a removed one a `REJECTED` request, in the same commit |
| `teams`, `shifts`, `missions` | Any signed-in user | **Temporary**: any signed-in user |

A collection-group rule (`/{path=**}/membershipRequests/{uid}`) lets a volunteer list their own
requests across all events.

A withdrawal is the one exception to the table above: a volunteer may delete their own request
and, in the same commit, remove themself (and only themself) from `eventParticipants`. A request
can only be deleted once its volunteer is no longer a participant.

## Known gaps

- **`teams`, `shifts` and `missions` rules are temporary.** A follow-up PR will restrict writes to
  the organizer (`isOrganizer(eventId)`), and reads to the organizer and the event's participants.
- **`Event.memberIds`** is deprecated in favour of `eventParticipants` and will be removed.
- **Deleting an event does not delete its subcollections.** Whoever implements event deletion has
  to remove them explicitly.
- **`User.createdAt`** is still read as a `java.util.Date`, unlike the other models, which use
  `Instant`.
- **Offline:** with Firestore's persistent cache, a query made offline returns cached data, which
  can be empty or stale, rather than failing. Transactions need a connection.
