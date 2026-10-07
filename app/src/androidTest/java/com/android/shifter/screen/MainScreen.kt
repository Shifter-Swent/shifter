// Co-authored-by: OpenAI Codex <noreply@openai.com>
package com.swent.shifter.screen

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.swent.shifter.authentication.SignInScreenTestTags
import com.swent.shifter.resources.C
import io.github.kakaocup.compose.node.element.ComposeScreen
import io.github.kakaocup.compose.node.element.KNode

class MainScreen(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<MainScreen>(
        semanticsProvider = semanticsProvider,
        viewBuilderAction = { hasTestTag(C.Tag.main_screen_container) },
    ) {

  val appName: KNode = child { hasTestTag(SignInScreenTestTags.APP_NAME) }
  val signInButton: KNode = child { hasTestTag(SignInScreenTestTags.LOGIN_BUTTON) }
}
