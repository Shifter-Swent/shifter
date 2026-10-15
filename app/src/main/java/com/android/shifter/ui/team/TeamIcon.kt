// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.swent.shifter.R

/**
 * An icon a team can be shown with, as offered by the team creation form.
 *
 * Which icons exist is the screen's business, as [com.swent.shifter.ui.team.CreateTeamUiState.icon]
 * says: [id] is the only part that leaves it, stored as [com.swent.shifter.model.team.Team.icon].
 * Offering one more icon is one more entry here, with its drawable and its label.
 *
 * @property id the identifier persisted with the team. Stable: changing one would leave the teams
 *   already created with it without an icon.
 * @property drawable the glyph of the designs.
 * @property label what the icon stands for, shown next to it in the picker.
 */
enum class TeamIcon(
    val id: String,
    @get:DrawableRes val drawable: Int,
    @get:StringRes val label: Int,
) {
  TRUCK("truck", R.drawable.ic_truck, R.string.create_team_icon_truck),
  WINE("wine", R.drawable.ic_wine, R.string.create_team_icon_wine),
  CHEF_HAT("chef-hat", R.drawable.ic_chef_hat, R.string.create_team_icon_chef_hat);

  companion object {
    /** The icon [id] stands for, or null when none has been chosen yet or it is not offered. */
    fun fromId(id: String?): TeamIcon? = entries.firstOrNull { it.id == id }
  }
}
