// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
import com.swent.shifter.ui.theme.ShifterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The card on its own: what it shows and what it reports. It is given a state and hands back edits,
 * so nothing here knows about [CreateTeamViewModel]; which state follows which edit is the
 * ViewModel's business, and the screen that connects the two is not part of this card.
 */
@RunWith(AndroidJUnit4::class)
class CreateTeamContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  /** The card over a fixed state, for everything that does not need the state to move. */
  private fun setCard(state: CreateTeamUiState) {
    composeTestRule.setContent { ShifterTheme { CreateTeamContent(state, CreateTeamActions()) } }
  }

  /**
   * The card over a state it edits itself, which is what a screen gives it. Returns a reader of
   * that state, so a test can check what the card made of the edits it reported.
   */
  private fun setEditableCard(
      initial: CreateTeamUiState = CreateTeamUiState()
  ): () -> CreateTeamUiState {
    val holder = mutableStateOf(initial)
    composeTestRule.setContent {
      var state by remember { holder }
      ShifterTheme {
        CreateTeamContent(
            state = state,
            actions =
                CreateTeamActions(
                    onNameChange = { state = state.copy(name = it) },
                    onIconChange = { state = state.copy(icon = it) },
                    onDescriptionChange = { state = state.copy(description = it) },
                ),
        )
      }
    }
    return { holder.value }
  }

  @Test
  fun displaysNameIconAndDescription() {
    setCard(CreateTeamUiState())

    listOf(
            CreateTeamFormTestTags.FORM,
            CreateTeamFormTestTags.NAME_FIELD,
            CreateTeamFormTestTags.ICON_SELECTOR,
            CreateTeamFormTestTags.DESCRIPTION_FIELD,
        )
        .forEach { node(it).assertIsDisplayed() }
  }

  /** The fields are BasicTextFields, which do not carry their label on their own. */
  @Test
  fun fieldsExposeTheirLabelToAccessibility() {
    setCard(CreateTeamUiState())

    node(CreateTeamFormTestTags.NAME_FIELD)
        .assert(hasContentDescription(context.getString(R.string.create_team_field_name)))
    node(CreateTeamFormTestTags.DESCRIPTION_FIELD)
        .assert(hasContentDescription(context.getString(R.string.create_team_field_description)))
  }

  @Test
  fun typingGoesThroughTheCardIntoTheState() {
    val read = setEditableCard()

    node(CreateTeamFormTestTags.NAME_FIELD).performTextInput("Bar")
    node(CreateTeamFormTestTags.DESCRIPTION_FIELD).performTextInput("Serve the drinks.")

    assertEquals("Bar", read().name)
    assertEquals("Serve the drinks.", read().description)
  }

  /** Over the catalogue itself, so that offering one more icon does not break this. */
  @Test
  fun theSelectorDisplaysAllAvailableIcons() {
    setCard(CreateTeamUiState())

    node(CreateTeamFormTestTags.ICON_SELECTOR).performClick()

    TeamIcon.entries.forEach { icon ->
      node(CreateTeamFormTestTags.iconOption(icon))
          .assertIsDisplayed()
          .assertTextEquals(context.getString(icon.label))
    }
  }

  @Test
  fun pickingAnIcon_reportsItClosesTheMenuAndAnnouncesIt() {
    val read = setEditableCard()
    node(CreateTeamFormTestTags.ICON_SELECTOR)
        .assert(hasContentDescription(context.getString(R.string.create_team_icon_none)))

    node(CreateTeamFormTestTags.ICON_SELECTOR).performClick()
    node(CreateTeamFormTestTags.iconOption(TeamIcon.WINE)).performClick()

    assertEquals("wine", read().icon)
    TeamIcon.entries.forEach {
      composeTestRule.onNodeWithTag(CreateTeamFormTestTags.iconOption(it)).assertDoesNotExist()
    }
    // The glyph is the only visual sign of the choice, so the selector has to say it out loud.
    node(CreateTeamFormTestTags.ICON_SELECTOR)
        .assert(
            hasContentDescription(
                context.getString(
                    R.string.create_team_icon_selected,
                    context.getString(R.string.create_team_icon_wine),
                )
            )
        )
  }
}
