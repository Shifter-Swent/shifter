// Co-authored-by: OpenAI Codex <noreply@openai.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.swent.shifter.model.RepositoryProviderFactory
import com.swent.shifter.resources.C
import com.swent.shifter.ui.navigation.ShifterNavHost
import com.swent.shifter.ui.theme.ShifterTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val repositories = RepositoryProviderFactory.production
    // Firebase restores the previous session synchronously, so a returning user skips sign-in.
    val isSignedIn = repositories.authRepository.currentUser() != null
    setContent {
      ShifterTheme {
        // A surface container using the 'background' color from the theme
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          ShifterNavHost(isSignedIn = isSignedIn, repositories = repositories)
        }
      }
    }
  }
}
