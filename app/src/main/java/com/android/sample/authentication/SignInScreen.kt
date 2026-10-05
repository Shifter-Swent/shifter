package com.swent.shifter.authentication

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.ShifterTheme

object SignInScreenTestTags {
  const val APP_NAME = "APP_NAME"
  const val LOGIN_BUTTON = "LOGIN_BUTTON"
  const val LOGIN_TITLE = "LOGIN_TITLE"
}

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit = {},
) {
  val colors = MaterialTheme.colorScheme

  Column(
      modifier = Modifier.fillMaxSize().background(colors.background).padding(horizontal = 43.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
  ) {
    Text(
        text = "Shifter",
        color = colors.onBackground,
        fontSize = 44.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(SignInScreenTestTags.APP_NAME),
    )

    Spacer(modifier = Modifier.height(32.dp))

    Button(
        onClick = onSignedIn,
        modifier = Modifier.size(334.dp, 58.dp).testTag(SignInScreenTestTags.LOGIN_BUTTON),
        shape = RoundedCornerShape(18.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.google_icon),
            contentDescription = "Google",
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "Continue with Google",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
      }
    }

    Spacer(modifier = Modifier.height(18.dp))

    Text(
        text = "Create your account or sign in\nwith your Google account.",
        color = colors.secondary,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag(SignInScreenTestTags.LOGIN_TITLE),
    )
  }
}

@Preview(showBackground = true)
@Composable
fun SignInScreenPreview() {
  ShifterTheme { SignInScreen() }
}
