// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>

package com.swent.shifter.model.authentication

/**
 * The signed-in account, independent of the authentication provider.
 *
 * @property uid Unique id of the account, also used as the Firestore user document id.
 * @property email Account email, `null` if the provider did not share one.
 * @property displayName Account name, `null` if the provider did not share one.
 */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
)
