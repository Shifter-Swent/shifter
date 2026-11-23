// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.theme

import androidx.compose.ui.graphics.Color

val md_theme_dark_primary = Color(0xFF2F80FF)
val md_theme_dark_onPrimary = Color(0xFFF7F9FF)
val md_theme_dark_primaryContainer = Color(0xFF1A2A4A)
val md_theme_dark_onPrimaryContainer = Color(0xFFF7F9FF)

val md_theme_dark_secondary = Color(0xFF95A4BB)
val md_theme_dark_onSecondary = Color(0xFF151D27)
val md_theme_dark_secondaryContainer = Color(0xFF1C2A3A)
val md_theme_dark_onSecondaryContainer = Color(0xFFF7F9FF)

// Même accent bleu : pas de troisième accent global défini.
val md_theme_dark_tertiary = Color(0xFF2F80FF)
val md_theme_dark_onTertiary = Color(0xFFF7F9FF)
val md_theme_dark_tertiaryContainer = Color(0xFF1A2A4A)
val md_theme_dark_onTertiaryContainer = Color(0xFFF7F9FF)

val md_theme_dark_error = Color(0xFFE87070)
val md_theme_dark_onError = Color(0xFF151D27)
val md_theme_dark_errorContainer = Color(0xFF291C2B)
val md_theme_dark_onErrorContainer = Color(0xFFFF8D9B)

val md_theme_dark_outline = Color(0xFF2F3E55)
val md_theme_dark_background = Color(0xFF151D27)
val md_theme_dark_onBackground = Color(0xFFF7F9FF)
val md_theme_dark_surface = Color(0xFF151D27)
val md_theme_dark_onSurface = Color(0xFFF7F9FF)
val md_theme_dark_surfaceVariant = Color(0xFF1C2A3A)
val md_theme_dark_onSurfaceVariant = Color(0xFF95A4BB)
val md_theme_dark_surfaceContainer = Color(0xFF192337)

val md_theme_dark_inverseSurface = Color(0xFFF7F9FF)
val md_theme_dark_inverseOnSurface = Color(0xFF151D27)
val md_theme_dark_inversePrimary = Color(0xFF2F80FF)
val md_theme_dark_surfaceTint = Color(0xFF2F80FF)
val md_theme_dark_outlineVariant = Color(0xFF2A3A4F)
val md_theme_dark_scrim = Color(0xFF000000)

val shifter_success = Color(0xFF34D399)
val shifter_successContainer = Color(0xFF0D3D24)
val shifter_warning = Color(0xFFF5A623)
val shifter_warningContainer = Color(0xFF2A2000)
val shifter_inputBackground = Color(0xFF101722)
val shifter_mutedText = Color(0xFF6B85A1)
/** Muted text on the primary blue, such as the time of the user's own message. */
val shifter_onPrimaryMuted = Color(0xB3D6E6FF)
/** Text that is there but has nothing behind it, such as a day without missions. */
val shifter_dimmedText = Color(0xFF3D4F66)
val shifter_tabInactive = Color(0xFF4A5E78)
val shifter_divider = Color(0xFF2A3547)
val shifter_avatarBackground = Color(0xFF2F3E55)
val shifter_signoutOutline = Color(0xFF493044)
val shifter_locationIconBackground = Color(0xFF203452)

// The week grid of shifts: its own tones, darker than the surfaces, so a grid of dozens of cells
// stays readable. A slot the volunteers already chose uses primaryContainer, and the line between
// two cells shifter_divider.
val shifter_shiftOpen = Color(0xFF1C365E)
val shifter_shiftClosed = Color(0xFF18212D)
/** A slot the organizer can still open, a shade above a closed one so the "+" invites a tap. */
val shifter_shiftOpenable = Color(0xFF1A2433)
/** The line inside a block of slots that carries on past a cell, fainter than a grid line. */
val shifter_shiftJoin = Color(0xFF15294A)
val shifter_onShiftLocked = Color(0xFFCFE0FF)

// A QR code stays dark on light whatever the theme, so every scanner reads it.
val shifter_qrDark = Color(0xFF000000)
val shifter_qrLight = Color(0xFFFFFFFF)

// The background of a channel, a shade darker than the surface so its bubbles and cards stand
// out.
val shifter_chatBackground = Color(0xFF10171F)

// Announcements: the warning amber for the author, a dark amber card that keeps the usual text
// readable on it, and a dimmed amber for the time.
val shifter_announcement = shifter_warning
val shifter_onAnnouncement = md_theme_dark_surface
val shifter_announcementContainer = Color(0xFF2A2418)
val shifter_announcementMuted = Color(0xFFA8916A)
val shifter_onAnnouncementContainer = md_theme_dark_onSurface

// The tints of the Discussions, one per team or person: soft enough to read as a name on the dark
// surface and to carry dark text as an avatar.
val shifter_chatTintPink = Color(0xFFF4B4B4)
val shifter_chatTintGreen = Color(0xFFB5E3A8)
val shifter_chatTintBlue = Color(0xFFAFC6F7)
val shifter_chatTintAmber = Color(0xFFF2D28C)
val shifter_chatTintPurple = Color(0xFFD3B8F2)
val shifter_chatTintTeal = Color(0xFF9EDCDC)
