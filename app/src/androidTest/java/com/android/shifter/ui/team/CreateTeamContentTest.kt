// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.swent.shifter.R
import com.swent.shifter.model.team.Team
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The card on its own: what it shows, what it reports and what it refuses while saving. Nothing
 * here knows about [CreateTeamViewModel] — the card is given a state and hands back edits. Which
 * state follows which edit is the ViewModel's business, tested over the whole form in
 * [CreateTeamFormTest].
 */
@RunWith(AndroidJUnit4::class)
class CreateTeamContentTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

  private fun node(tag: String) = composeTestRule.onNodeWithTag(tag)

  /** Errors sit next to their field rather than inside it, so the unmerged tree finds them. */
  private fun errorNode(field: TeamFormField) =
      composeTestRule.onNodeWithTag(CreateTeamFormTestTags.error(field), useUnmergedTree = true)

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
  fun opening_displaysNameIconAndDescription() {
    setCard(CreateTeamUiState())

    listOf(
            CreateTeamFormTestTags.FORM,
            CreateTeamFormTestTags.NAME_FIELD,
            CreateTeamFormTestTags.ICON_SELECTOR,
            CreateTeamFormTestTags.DESCRIPTION_FIELD,
        )
        .forEach { node(it).assertIsDisplayed() }
  }

  /** The fields are BasicTextFields, which carry neither their label nor their error by default. */
  @Test
  fun fields_exposeTheirLabelAndErrorToAccessibility() {
    setCard(CreateTeamUiState(errors = setOf(TeamFormError.NAME_EMPTY)))

    node(CreateTeamFormTestTags.NAME_FIELD)
        .assert(hasContentDescription(context.getString(R.string.create_team_field_name)))
        .assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Error,
                context.getString(R.string.create_team_error_name_empty),
            )
        )
    node(CreateTeamFormTestTags.DESCRIPTION_FIELD)
        .assert(hasContentDescription(context.getString(R.string.create_team_field_description)))
  }

  @Test
  fun typing_goesThroughTheCardIntoTheState() {
    val read = setEditableCard()

    node(CreateTeamFormTestTags.NAME_FIELD).performTextInput("Bar")
    node(CreateTeamFormTestTags.DESCRIPTION_FIELD).performTextInput("Serve the drinks.")

    assertEquals("Bar", read().name)
    assertEquals("Serve the drinks.", read().description)
  }

  /** Over the catalogue itself, so that offering one more icon does not break this. */
  @Test
  fun iconSelector_displaysAllAvailableIcons() {
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

  /**
   * The chosen entry is told apart by its colour alone, so the menu says which one it is; a test
   * can then ask, rather than compare pixels.
   */
  @Test
  fun theMenuMarksTheChosenIcon_andFollowsANewChoice() {
    setEditableCard(CreateTeamUiState(icon = TeamIcon.WINE.id))

    node(CreateTeamFormTestTags.ICON_SELECTOR).performClick()

    node(CreateTeamFormTestTags.iconOption(TeamIcon.WINE)).assertIsSelected()
    TeamIcon.entries
        .filter { it != TeamIcon.WINE }
        .forEach { node(CreateTeamFormTestTags.iconOption(it)).assertIsNotSelected() }

    // Picking another one closes the menu, which then opens on that one instead.
    node(CreateTeamFormTestTags.iconOption(TeamIcon.TRUCK)).performClick()
    node(CreateTeamFormTestTags.ICON_SELECTOR).performClick()

    node(CreateTeamFormTestTags.iconOption(TeamIcon.TRUCK)).assertIsSelected()
    node(CreateTeamFormTestTags.iconOption(TeamIcon.WINE)).assertIsNotSelected()
  }

  @Test
  fun errors_areShownBesideTheirOwnField() {
    setCard(CreateTeamUiState(errors = setOf(TeamFormError.NAME_EMPTY, TeamFormError.ICON_MISSING)))

    errorNode(TeamFormField.NAME)
        .assertTextEquals(context.getString(R.string.create_team_error_name_empty))
    errorNode(TeamFormField.ICON)
        .assertTextEquals(context.getString(R.string.create_team_error_icon_missing))
    errorNode(TeamFormField.DESCRIPTION).assertDoesNotExist()
  }

  @Test
  fun lengthErrors_showTheLimitTheyAreAbout() {
    setCard(
        FILLED.copy(errors = setOf(TeamFormError.NAME_TOO_LONG, TeamFormError.DESCRIPTION_TOO_LONG))
    )

    errorNode(TeamFormField.NAME)
        .assertTextEquals(
            context.resources.getQuantityString(
                R.plurals.create_team_error_too_long,
                CreateTeamViewModel.NAME_MAX_LENGTH,
                CreateTeamViewModel.NAME_MAX_LENGTH,
            )
        )
    errorNode(TeamFormField.DESCRIPTION)
        .assertTextEquals(
            context.resources.getQuantityString(
                R.plurals.create_team_error_too_long,
                CreateTeamViewModel.DESCRIPTION_MAX_LENGTH,
                CreateTeamViewModel.DESCRIPTION_MAX_LENGTH,
            )
        )
  }

  @Test
  fun whileSaving_nothingCanBeEdited() {
    assertNothingCanBeEdited(setEditableCard(FILLED.copy(isSaving = true)))
  }

  /** Once the team exists the card is closed too, until the screen takes it away. */
  @Test
  fun afterCreation_nothingCanBeEdited() {
    assertNothingCanBeEdited(setEditableCard(FILLED.copy(createdTeam = CREATED_TEAM)))
  }

  /** Nothing goes in through the card: no typing, and the catalogue does not even open. */
  private fun assertNothingCanBeEdited(read: () -> CreateTeamUiState) {
    node(CreateTeamFormTestTags.NAME_FIELD).assertIsNotEnabled()
    node(CreateTeamFormTestTags.DESCRIPTION_FIELD).assertIsNotEnabled()
    node(CreateTeamFormTestTags.ICON_SELECTOR).assertIsNotEnabled()

    // A disabled selector must not open the catalogue either.
    node(CreateTeamFormTestTags.ICON_SELECTOR).performClick()
    TeamIcon.entries.forEach {
      composeTestRule.onNodeWithTag(CreateTeamFormTestTags.iconOption(it)).assertDoesNotExist()
    }
    assertNull(read().icon)
  }

  private companion object {
    val FILLED = CreateTeamUiState(name = "Logistics")

    /** A team as the ViewModel reports it once the backend has stored it. */
    val CREATED_TEAM =
        Team(
            id = "team-1",
            eventId = "event-1",
            name = "Logistics",
            description = "Set up the site and bring in the equipment.",
            icon = TeamIcon.TRUCK.id,
            createdAt = Instant.EPOCH,
        )
  }
}
