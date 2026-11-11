// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.ui.components

/** Up to [maxInitials] word initials, preserving Unicode code points; `?` without a name. */
fun avatarInitials(name: String?, maxInitials: Int = 2): String =
    name
        ?.trim()
        ?.splitToSequence(Regex("[\\s\\p{Z}]+"))
        ?.filter { it.isNotEmpty() }
        ?.take(maxInitials)
        ?.joinToString("") { word -> word.substring(0, word.offsetByCodePoints(0, 1)).uppercase() }
        ?.ifEmpty { null } ?: "?"
