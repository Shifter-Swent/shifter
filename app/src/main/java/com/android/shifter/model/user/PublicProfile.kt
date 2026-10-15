// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.model.user

/**
 * What any signed-in user may see of another user, stored in Firestore at `publicProfiles/{uid}`.
 * Private data, such as the email, stays in [User], which only its owner reads.
 *
 * @property uid Firebase Auth uid, also used as the document id.
 * @property displayName Name shown to the organizer and the team, the same as [User.displayName].
 */
data class PublicProfile(val uid: String, val displayName: String)
