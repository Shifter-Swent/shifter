// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import com.swent.shifter.model.authentication.AuthUser

/** The first letter of [user]'s name, or of their email without one; `?` when there is neither. */
fun avatarInitial(user: AuthUser?): String =
    listOfNotNull(user?.displayName, user?.email)
        .firstNotNullOfOrNull { it.trim().firstOrNull() }
        ?.uppercase() ?: "?"
