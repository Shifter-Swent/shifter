// Co-authored-by: Claude Opus 5 <noreply@anthropic.com>
package com.swent.shifter.ui.team

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.shifter.ui.theme.ShifterTheme

// Previews of CreateTeamFormContent, kept apart from the form itself: they are tooling the IDE
// renders and no test ever runs, so coverage is told to leave this file alone.

/** The inline form on the background it is dropped onto, as the previews below show it. */
@Composable
private fun PreviewHost(state: CreateTeamUiState) {
  ShifterTheme {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp)) {
      CreateTeamFormContent(
          state = state,
          actions = CreateTeamActions(),
          onSubmit = {},
          onCancel = {},
      )
    }
  }
}

private val FILLED_FORM =
    CreateTeamUiState(
        name = "Logistics",
        icon = TeamIcon.TRUCK.id,
        description = "Set up the site and bring in the equipment.",
    )

@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun CreateTeamFormEmptyPreview() {
  PreviewHost(CreateTeamUiState())
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun CreateTeamFormFilledPreview() {
  PreviewHost(FILLED_FORM)
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun CreateTeamFormInvalidPreview() {
  PreviewHost(
      CreateTeamUiState(
          errors =
              setOf(
                  TeamFormError.NAME_EMPTY,
                  TeamFormError.ICON_MISSING,
                  TeamFormError.DESCRIPTION_EMPTY,
              )
      )
  )
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun CreateTeamFormSavingPreview() {
  PreviewHost(FILLED_FORM.copy(isSaving = true))
}

@Preview(showBackground = true, backgroundColor = 0xFF151D27)
@Composable
private fun CreateTeamFormSaveFailedPreview() {
  PreviewHost(FILLED_FORM.copy(saveFailed = true))
}
