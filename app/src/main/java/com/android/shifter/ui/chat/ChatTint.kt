// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.swent.shifter.ui.theme.md_theme_dark_surface
import com.swent.shifter.ui.theme.shifter_chatTintAmber
import com.swent.shifter.ui.theme.shifter_chatTintBlue
import com.swent.shifter.ui.theme.shifter_chatTintGreen
import com.swent.shifter.ui.theme.shifter_chatTintPink
import com.swent.shifter.ui.theme.shifter_chatTintPurple
import com.swent.shifter.ui.theme.shifter_chatTintTeal

/** The tints of the Discussions: a team's channel tile, and the name and avatar of a member. */
internal val CHAT_TINTS =
    listOf(
        shifter_chatTintPink,
        shifter_chatTintGreen,
        shifter_chatTintBlue,
        shifter_chatTintAmber,
        shifter_chatTintPurple,
        shifter_chatTintTeal,
    )

/**
 * The tint of [key] (a team id or a member's user id), always the same for the same key, so a team
 * or a person keeps their color from one screen and one day to the next. An id rather than a name,
 * so two members with the same name get their own tint, and a renamed one keeps theirs.
 */
internal fun chatTint(key: String): Color =
    CHAT_TINTS[Math.floorMod(key.hashCode(), CHAT_TINTS.size)]

/** The background of a tile tinted [tint]: the tint, faint on the dark surface. */
internal fun tileBackground(tint: Color): Color = lerp(md_theme_dark_surface, tint, 0.18f)
