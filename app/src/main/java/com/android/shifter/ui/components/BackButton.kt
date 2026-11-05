// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.shifter.R
import com.swent.shifter.ui.navigation.NavigationTestTags

object BackButtonTestTags {
  const val CIRCLE = "BackButtonCircle"
}

/**
 * The back button of every screen: a chevron in a circle, placed by the caller's [modifier]. It
 * takes 48 dp in the layout, for its touch target, so its 40 dp circle sits 4 dp inside its edge.
 * Show one per screen: its test tag is fixed, so two would make tests find two nodes.
 */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = MaterialTheme.colorScheme
  // IconButton keeps the 48 dp touch target itself, with a 40 dp container inside.
  IconButton(
      onClick = onClick,
      modifier = modifier.testTag(NavigationTestTags.GO_BACK_BUTTON),
      shape = CircleShape,
      colors =
          IconButtonDefaults.iconButtonColors(
              containerColor = colors.secondaryContainer,
              contentColor = colors.onSecondaryContainer,
          ),
  ) {
    Box(
        modifier = Modifier.fillMaxSize().testTag(BackButtonTestTags.CIRCLE),
        contentAlignment = Alignment.Center,
    ) {
      Icon(
          painter = painterResource(R.drawable.ic_chevron_left),
          contentDescription = stringResource(R.string.content_description_back),
          modifier = Modifier.size(18.dp),
      )
    }
  }
}
