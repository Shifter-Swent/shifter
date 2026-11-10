// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.e2e

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.swent.shifter.R
import com.swent.shifter.firebase.AuthEmulator
import com.swent.shifter.firebase.FirestoreEmulator
import com.swent.shifter.firebase.FirestoreEmulatorAdmin
import com.swent.shifter.model.RepositoryProviderFactory
import com.swent.shifter.model.event.EventType
import com.swent.shifter.ui.authentication.SignInScreenTestTags
import com.swent.shifter.ui.event.CreateEventScreenTestTags
import com.swent.shifter.ui.event.JoinEventScreenTestTags
import com.swent.shifter.ui.events.MyEventsTestTags
import com.swent.shifter.ui.mission.AddMissionScreenTestTags
import com.swent.shifter.ui.navigation.ShifterNavHost
import com.swent.shifter.ui.settings.SettingsScreenTestTags
import com.swent.shifter.ui.team.CreateTeamFormTestTags
import com.swent.shifter.ui.team.TeamIcon
import com.swent.shifter.ui.team.TeamSetupScreenTestTags
import com.swent.shifter.ui.theme.ShifterTheme
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.runBlocking

/**
 * Drives the whole app through its screens for the end-to-end tests, as several people use it on
 * one phone, on the Auth and Firestore emulators.
 *
 * Only the Google account picker is replaced, as the system one cannot be driven: it answers with
 * whichever account [signInAs] is about to use. Every document a test writes is registered in
 * [createdPaths] and deleted by [cleanUp], with the accounts.
 */
class EndToEndDriver(
    private val composeTestRule:
        AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>
) {

  /** The production repositories, on the emulators, which the tests also read Firestore with. */
  val repositories =
      RepositoryProviderFactory.create(FirestoreEmulator.firestore, AuthEmulator.auth)

  val context: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

  /** Documents written by the test, deleted afterwards, children before their parents. */
  val createdPaths = mutableListOf<String>()

  /** The account the picker answers with on the next sign-in. */
  private var pickedAccount: GoogleAccount? = null

  private val accountPicker =
      object :
          CredentialManager by CredentialManager.create(
              InstrumentationRegistry.getInstrumentation().targetContext
          ) {
        override suspend fun getCredential(
            context: Context,
            request: GetCredentialRequest,
        ): GetCredentialResponse =
            GetCredentialResponse(checkNotNull(pickedAccount) { "No account picked" }.credential())
      }

  /** Shows the app signed out, as on its first launch. */
  fun launchApp() {
    AuthEmulator.auth.signOut()
    composeTestRule.setContent {
      ShifterTheme {
        ShifterNavHost(
            isSignedIn = false,
            repositories = repositories,
            credentialManager = accountPicker,
        )
      }
    }
  }

  /** Deletes what the test wrote, then signs out and removes the accounts. */
  fun cleanUp() {
    runBlocking { createdPaths.forEach { FirestoreEmulatorAdmin.deleteDocument(it) } }
    AuthEmulator.auth.signOut()
    AuthEmulator.clearAccounts()
  }

  /**
   * Signs in through the sign-in screen, the picker answering with [account], and waits for the
   * volunteer events every sign-in opens on. Returns the user's id and registers their profile for
   * cleanup.
   */
  fun signInAs(account: GoogleAccount): String {
    pickedAccount = account
    waitForNode(SignInScreenTestTags.LOGIN_BUTTON)
    click(SignInScreenTestTags.LOGIN_BUTTON)
    waitForNode(MyEventsTestTags.STAFF_SCREEN)
    val uid = checkNotNull(repositories.authRepository.currentUser()).uid
    if ("users/$uid" !in createdPaths) {
      createdPaths += "users/$uid"
      createdPaths += "publicProfiles/$uid"
    }
    return uid
  }

  /** Signs out from Settings, opened from the avatar of the events list on screen. */
  fun signOut() {
    click(MyEventsTestTags.AVATAR)
    click(SettingsScreenTestTags.SIGN_OUT_BUTTON)
    waitForNode(SignInScreenTestTags.LOGIN_BUTTON)
  }

  /**
   * Fills every field of the event creation form with valid values, the event lasting from [start]
   * to [end], and submits it.
   */
  fun fillAndSubmitEventForm(title: String, start: LocalDateTime, end: LocalDateTime) {
    fun field(tag: String) = composeTestRule.onNodeWithTag(tag).performScrollTo()
    field(CreateEventScreenTestTags.TITLE_FIELD).performTextInput(title)
    field(CreateEventScreenTestTags.DESCRIPTION_FIELD).performTextInput("Open-air festival")
    field(CreateEventScreenTestTags.typeChip(EventType.MUSIC)).performClick()
    field(CreateEventScreenTestTags.ADDRESS_FIELD).performTextInput("Nyon")
    field(CreateEventScreenTestTags.START_FIELD).performTextInput(start.format(EVENT_TIME))
    field(CreateEventScreenTestTags.END_FIELD).performTextInput(end.format(EVENT_TIME))
    field(CreateEventScreenTestTags.SUBMIT_BUTTON).performClick()
  }

  /** Fills the open team form with a team called [name] and creates it. */
  fun createTeam(name: String) {
    fun inForm(tag: String) =
        composeTestRule.onNode(
            hasTestTag(tag) and hasAnyAncestor(hasTestTag(TeamSetupScreenTestTags.ACTIVE_FORM))
        )
    inForm(CreateTeamFormTestTags.NAME_FIELD).performTextInput(name)
    inForm(CreateTeamFormTestTags.ICON_SELECTOR).performClick()
    composeTestRule.onNodeWithTag(CreateTeamFormTestTags.iconOption(TeamIcon.TRUCK)).performClick()
    inForm(CreateTeamFormTestTags.DESCRIPTION_FIELD).performTextInput("What $name does")
    inForm(CreateTeamFormTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule
          .onAllNodesWithTag(TeamSetupScreenTestTags.ACTIVE_FORM)
          .fetchSemanticsNodes()
          .isEmpty()
    }
  }

  /**
   * From the volunteer events, finds the event titled [eventTitle] by [joinCode] and applies to the
   * team [teamId].
   */
  fun applyWithCode(joinCode: String, eventTitle: String, teamId: String) {
    click(MyEventsTestTags.SCAN_QR_BUTTON)
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.JOIN_CODE_FIELD)
        .performTextInput(joinCode)
    click(JoinEventScreenTestTags.FIND_BUTTON)
    waitForNode(JoinEventScreenTestTags.teamOption(teamId))
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.EVENT_TITLE)
        .assertTextContains(eventTitle)
    composeTestRule
        .onNodeWithTag(JoinEventScreenTestTags.teamOption(teamId))
        .performScrollTo()
        .performClick()
    // The button stays pinned under the event, out of the scrolling part.
    click(JoinEventScreenTestTags.APPLY_BUTTON)
    waitForNode(JoinEventScreenTestTags.APPLIED_MESSAGE)
  }

  /**
   * Fills the mission form for [teamId], from the event's start to an hour later, and saves it. The
   * start picker opens on the event's start, which starts at [startHour]; the end one is moved an
   * hour later on its dial.
   */
  fun addMissionForTeam(teamId: String, title: String, startHour: Int) {
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.TITLE_FIELD).performTextInput(title)
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.DESCRIPTION_FIELD)
        .performTextInput("Build the stage before the first concert")
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.teamChip(teamId))
        .performScrollTo()
        .performClick()
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.START_FIELD)
        .performScrollTo()
        .performClick()
    click(AddMissionScreenTestTags.PICKER_CONFIRM)
    composeTestRule.onNodeWithTag(AddMissionScreenTestTags.END_FIELD).performClick()
    // The 24-hour picker names each hour of its dial "<hour> hours", in English.
    composeTestRule.onNodeWithContentDescription("${(startHour + 1) % 24} hours").performClick()
    click(AddMissionScreenTestTags.PICKER_CONFIRM)
    composeTestRule
        .onNodeWithTag(AddMissionScreenTestTags.SUBMIT_BUTTON)
        .performScrollTo()
        .performClick()
  }

  /** The text of an event card's status pill, as the screen formats [label]. */
  fun badge(label: Int) =
      context.getString(R.string.my_events_status_pill, context.getString(label))

  /** Presses the device Back until the screen tagged [tag] shows, as a user would. */
  fun backTo(tag: String) {
    repeat(MAX_BACK_PRESSES) {
      if (composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) return
      composeTestRule.waitForIdle()
      composeTestRule.runOnUiThread {
        composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
      }
      composeTestRule.waitForIdle()
    }
    waitForNode(tag)
  }

  fun click(tag: String) {
    waitForNode(tag)
    composeTestRule.onNodeWithTag(tag).performClick()
  }

  fun waitForNode(tag: String) {
    composeTestRule.waitUntil(TIMEOUT_MILLIS) {
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  fun textOf(node: SemanticsNodeInteraction): String =
      node.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

  /** The text of the node tagged [tag], or null while it is not on screen. */
  fun textOrNull(tag: String): String? =
      composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.let { node ->
        node.config
            .getOrElseNullable(SemanticsProperties.Text) { null }
            ?.joinToString("") { it.text }
      }

  /**
   * A Google account, the same person on every sign-in: the Auth emulator maps its fixed subject to
   * the same Firebase user each time.
   */
  class GoogleAccount(private val name: String) {
    private val subject = "google-${UUID.randomUUID()}"
    private val email = "${name.lowercase()}-${UUID.randomUUID()}@example.com"

    fun credential(): GoogleIdTokenCredential =
        GoogleIdTokenCredential.Builder()
            .setId(email)
            .setIdToken(AuthEmulator.fakeGoogleIdToken(subject, email, name))
            .build()
  }

  companion object {
    const val TIMEOUT_MILLIS = 20_000L
    private const val MAX_BACK_PRESSES = 4

    /** How the event form reads its start and end. */
    private val EVENT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
  }
}
