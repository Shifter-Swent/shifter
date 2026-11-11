// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.events

import com.swent.shifter.model.authentication.AuthUser
import com.swent.shifter.ui.components.avatarInitials

/** The first letter of [user]'s name, or of their email without one; `?` when there is neither. */
fun avatarInitial(user: AuthUser?): String =
    avatarInitials(
        listOfNotNull(user?.displayName, user?.email).firstOrNull { it.isNotBlank() },
        maxInitials = 1,
    )
