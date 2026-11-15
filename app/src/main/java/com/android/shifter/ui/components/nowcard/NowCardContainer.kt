// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.components.nowcard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.ui.components.Pill
import com.swent.shifter.ui.components.PillColors
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_warning

/**
 * How much the now card asks for attention, ordered by urgency: the card's content slides up when
 * the tone moves to a later entry, and down when it moves back.
 */
enum class NowTone(
    /** How much of the tone the card's field takes: more when it starts soon or needs the user. */
    internal val tint: Float
) {
  /** Nothing soon: grey. */
  CALM(0f),
  /** A mission is running: green. */
  LIVE(0.08f),
  /** A mission starts soon: blue. */
  SOON(0.22f),
  /** Something needs the user: amber. */
  ALERT(0.22f),
}

/**
 * The tags of the now card and its parts. A card that names its own tags (the manager's) aliases
 * these, e.g. `const val TIME_BAR = NowCardTestTags.TIME_BAR`, rather than tagging a node twice.
 */
object NowCardTestTags {
  const val CARD = "nowCard"
  const val STATUS = "nowCardStatus"
  const val HEADLINE = "nowCardHeadline"
  /** Each [TimeBar] or [CountdownBar], the whole bar with its title and labels. */
  const val TIME_BAR = "nowCardTimeBar"
  /** The [ThenStrip]. */
  const val NEXT = "nowCardNext"
  /** The card's one action, in its body or in its [ThenStrip]. */
  const val ACTION = "nowCardAction"
  /** Why the volunteer is not checked in yet, on the volunteer's card. */
  const val REASON = "nowCardReason"
}

/**
 * The tone's color, for the band, the flash and the parts the cards draw in it (a time bar). Read
 * from the theme, except the success and warning colors, which the color scheme does not have.
 */
val NowTone.accent: Color
  @Composable
  @ReadOnlyComposable
  get() =
      when (this) {
        NowTone.CALM -> MaterialTheme.colorScheme.onSurfaceVariant
        NowTone.LIVE -> shifter_success
        NowTone.SOON -> MaterialTheme.colorScheme.primary
        // ALERT: an else rather than the last tone, so the when has no branch no tone reaches.
        else -> shifter_warning
      }

private const val FLASH_ALPHA = 0.45f
private const val FLASH_MILLIS = 900

private val NowTone.field: Color
  @Composable
  @ReadOnlyComposable
  get() = lerp(MaterialTheme.colorScheme.surfaceContainer, accent, tint)

private val NowTone.pill: PillColors
  @Composable
  @ReadOnlyComposable
  get() {
    val colors = MaterialTheme.colorScheme
    return if (this == NowTone.CALM) PillColors(colors.onSurface, colors.surface)
    else PillColors(colors.surface, accent)
  }

/** What the card shows for one tone, kept by the outgoing content while it slides away. */
private data class NowFrame<T>(val tone: NowTone, val headline: String, val state: T)

/**
 * The shell of the now card, shared by the volunteer's and the manager's: a field tinted by [tone]
 * with a band of it on top, the [status] in a pill, the large [headline], then [content] and
 * [strip].
 *
 * When [tone] changes, the card flashes in the new tone, and the headline and the content slide up
 * if the tone is more urgent, down if it is less; the card's height follows. Within a tone, they
 * are updated in place, so a countdown in the headline does not slide every minute. The system
 * setting that turns animations off applies to all of it.
 *
 * [content] and [strip] draw the [state] they are given, not one they read elsewhere: while it
 * slides away, the old content is drawn with the old [state]. Compose keeps one instance of a
 * composable lambda and updates it, so only the state can tell the old content from the new. Give
 * an immutable value compared by its fields, with no callbacks in it: the lambdas capture those. If
 * the tone goes back before a slide ends, the content in between leaves without sliding.
 *
 * A card that names its own test tags (the manager's) aliases [NowCardTestTags.CARD] and
 * [NowCardTestTags.STATUS] rather than tagging the card a second time.
 *
 * @param state what [content] and [strip] draw, such as the card's state.
 * @param strip a part under the content, edge to edge, such as what comes next; nothing by default.
 * @param content the body under the headline, spaced like it.
 */
@Composable
fun <T> NowCardContainer(
    tone: NowTone,
    status: String,
    headline: String,
    state: T,
    modifier: Modifier = Modifier,
    strip: @Composable ColumnScope.(T) -> Unit = {},
    content: @Composable ColumnScope.(T) -> Unit = {},
) {
  val field by animateColorAsState(tone.field, label = "nowCardField")
  val accent = tone.accent
  val band by animateColorAsState(accent, label = "nowCardBand")
  val flash = rememberToneFlash(tone)
  val target = remember(tone, headline, state) { NowFrame(tone, headline, state) }

  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(20.dp))
              .drawBehind { drawRect(field) }
              .drawWithContent {
                drawContent()
                drawRect(accent, alpha = flash.value)
              }
              .testTag(NowCardTestTags.CARD)
  ) {
    Box(Modifier.fillMaxWidth().height(6.dp).drawBehind { drawRect(band) })
    TonePill(
        tone = tone,
        status = status,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp),
    )
    AnimatedContent(
        targetState = target,
        contentKey = { it.tone },
        transitionSpec = { slideWithUrgency() },
        label = "nowCardContent",
    ) { frame ->
      Column(Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Text(
              text = frame.headline,
              color = MaterialTheme.colorScheme.onSurface,
              fontSize = 30.sp,
              lineHeight = 32.sp,
              fontWeight = FontWeight.SemiBold,
              modifier = Modifier.testTag(NowCardTestTags.HEADLINE).semantics { heading() },
          )
          content(frame.state)
        }
        strip(frame.state)
      }
    }
  }
}

/**
 * The [status] in a pill filled with the [tone], its colors animated apart from the card so that
 * only the pill recomposes while they change. Its line is as tall as the text, as in the design,
 * rather than the theme's body line.
 */
@Composable
private fun TonePill(tone: NowTone, status: String, modifier: Modifier = Modifier) {
  val content by animateColorAsState(tone.pill.content, label = "nowCardPillContent")
  val container by animateColorAsState(tone.pill.container, label = "nowCardPillContainer")
  ProvideTextStyle(LocalTextStyle.current.copy(lineHeight = 15.sp)) {
    Pill(
        text = status,
        colors = PillColors(content, container),
        large = true,
        modifier =
            modifier.testTag(NowCardTestTags.STATUS).semantics {
              liveRegion = LiveRegionMode.Polite
            },
    )
  }
}

/**
 * Up when the new tone is more urgent, down when it is less; clipped under the pill. A change
 * within a tone is not a transition, but the size transform still animates the card's height.
 */
private fun <T> AnimatedContentTransitionScope<NowFrame<T>>.slideWithUrgency(): ContentTransform {
  val up = targetState.tone > initialState.tone
  val enter = slideInVertically { if (up) it else -it } + fadeIn()
  val exit = slideOutVertically { if (up) -it else it } + fadeOut()
  return enter togetherWith exit using SizeTransform()
}

/**
 * The alpha of the flash over the card: from [FLASH_ALPHA] to nothing each time [tone] changes, but
 * not when the card first shows, nor when the animations are off (it would flash for a frame).
 */
@Composable
private fun rememberToneFlash(tone: NowTone): Animatable<Float, *> {
  val flash = remember { Animatable(0f) }
  val shown = remember { arrayOf(tone) } // the last tone flashed, never read by the composition
  LaunchedEffect(tone) {
    if (tone == shown[0]) return@LaunchedEffect
    shown[0] = tone
    if (coroutineContext[MotionDurationScale]?.scaleFactor == 0f) return@LaunchedEffect
    flash.snapTo(FLASH_ALPHA)
    flash.animateTo(0f, tween(FLASH_MILLIS))
  }
  return flash
}
