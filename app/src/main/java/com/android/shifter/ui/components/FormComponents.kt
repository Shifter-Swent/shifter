// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Top of a form screen: a "‹ [backLabel]" link calling [onBack], above the screen [title].
 *
 * @param backButtonTestTag test tag of the back link.
 */
@Composable
fun FormHeader(backLabel: String, title: String, backButtonTestTag: String, onBack: () -> Unit) {
  Column(
      modifier =
          Modifier.fillMaxWidth()
              .background(MaterialTheme.colorScheme.background)
              .padding(start = 24.dp, top = 4.dp, end = 24.dp, bottom = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Row(
        modifier =
            Modifier.clip(MaterialTheme.shapes.small)
                .clickable(role = Role.Button, onClick = onBack)
                .testTag(backButtonTestTag),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
          Icons.AutoMirrored.Filled.KeyboardArrowLeft,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(14.dp),
      )
      Text(
          text = backLabel,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.primary,
      )
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
  }
}

/** The label shown above a form field. */
@Composable
fun FieldLabel(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

/**
 * A labelled text field of a form. A single-line field moves the focus to the next one on the IME
 * action.
 *
 * @param minLines lines shown when the field is empty, used only when [singleLine] is false.
 * @param supportingText shown under the field inside its frame, typically the error.
 */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    enabled: Boolean,
    testTag: String,
    singleLine: Boolean = true,
    minLines: Int = 3,
    supportingText: (@Composable () -> Unit)? = null,
) {
  val colorScheme = MaterialTheme.colorScheme
  Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
    FieldLabel(label)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        isError = isError,
        supportingText = supportingText,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = MaterialTheme.shapes.medium,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colorScheme.surfaceVariant,
                unfocusedContainerColor = colorScheme.surfaceVariant,
                disabledContainerColor = colorScheme.surfaceVariant,
                errorContainerColor = colorScheme.surfaceVariant,
                unfocusedBorderColor = colorScheme.outline,
                disabledBorderColor = colorScheme.outline,
            ),
        keyboardOptions =
            KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
  }
}

/**
 * The full-width main button of a form. While [isLoading], it shows a progress indicator instead of
 * [text].
 *
 * @param loadingIndicatorTestTag test tag of the progress indicator, none when null.
 */
@Composable
fun FormPrimaryButton(
    text: String,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    loadingIndicatorTestTag: String? = null,
) {
  Button(
      onClick = onClick,
      enabled = enabled,
      shape = MaterialTheme.shapes.medium,
      modifier = Modifier.fillMaxWidth().height(48.dp).testTag(testTag),
  ) {
    if (isLoading) {
      CircularProgressIndicator(
          modifier =
              Modifier.size(24.dp)
                  .then(loadingIndicatorTestTag?.let { Modifier.testTag(it) } ?: Modifier),
          color = MaterialTheme.colorScheme.onPrimary,
          strokeWidth = 2.dp,
      )
    } else {
      Text(
          text = text,
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold,
      )
    }
  }
}
