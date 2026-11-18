// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.swent.shifter.ui.scan

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.shifter.R
import com.swent.shifter.ui.theme.shifter_success
import com.swent.shifter.ui.theme.shifter_warning
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** What the viewfinder tells the volunteer about the last code the camera read. */
enum class ViewfinderState {
  /** No code yet: the scan line sweeps the frame. */
  SEARCHING,
  /** A join code was read: the corners turn green and tighten. */
  FOUND,
  /** A QR code that is not a Shifter event: the corners turn amber and shake. */
  UNKNOWN,
}

/**
 * Where to point the camera: the frame, with the camera darkened around it, and the hint under it.
 * Drawn over the camera image, so it is built and tested without a camera.
 *
 * While searching, a line sweeps the frame. A code read turns the corners green and tightens them
 * with a spring; an unknown code turns them amber and shakes them. Without the system animations,
 * the states only change colour and text.
 *
 * The frame is at most 265 dp, and smaller when less height is left, in landscape for example: give
 * the viewfinder a bounded height so the views under it stay on screen.
 *
 * @param refusals how many codes were refused so far: each new one shakes the corners again.
 */
@Composable
fun ScanViewfinder(state: ViewfinderState, modifier: Modifier = Modifier, refusals: Int = 0) {
  val animate = rememberAnimationsEnabled()
  val primary = MaterialTheme.colorScheme.primary
  val cornerColor =
      animateColorAsState(
          targetValue =
              if (state == ViewfinderState.FOUND) shifter_success
              else if (state == ViewfinderState.UNKNOWN) shifter_warning
              else MaterialTheme.colorScheme.onPrimary,
          animationSpec =
              if (animate) tween(COLOR_MILLIS, easing = FastOutSlowInEasing) else snap(),
          label = "corner colour",
      )
  val lineAlpha =
      animateFloatAsState(
          targetValue = if (animate && state == ViewfinderState.SEARCHING) 1f else 0f,
          animationSpec = tween(LINE_FADE_MILLIS),
          label = "scan line alpha",
      )
  // Down then up, each way eased: the line slows at the edges instead of bouncing off them.
  val sweep =
      rememberInfiniteTransition(label = "scan line")
          .animateFloat(
              initialValue = 0f,
              targetValue = 2f,
              animationSpec = infiniteRepeatable(tween(2 * SWEEP_MILLIS, easing = LinearEasing)),
              label = "scan line sweep",
          )
  val scale = remember { Animatable(1f) }
  LaunchedEffect(state, animate) {
    val target = if (state == ViewfinderState.FOUND) FOUND_SCALE else 1f
    if (animate) scale.animateTo(target, spring(dampingRatio = 0.55f, Spring.StiffnessMediumLow))
    else scale.snapTo(target)
  }
  val shake = remember { Animatable(0f) }
  LaunchedEffect(refusals, animate) {
    if (animate && state == ViewfinderState.UNKNOWN) {
      shake.animateTo(
          0f,
          keyframes {
            durationMillis = SHAKE_MILLIS
            6f at 60 using FastOutSlowInEasing
            -5f at 140 using FastOutSlowInEasing
            3f at 220 using FastOutSlowInEasing
            -1f at 300 using FastOutSlowInEasing
          },
      )
    }
  }

  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
        Modifier.weight(1f, fill = false)
            .layout { measurable, constraints ->
              // A square: a share of the width, at most 265 dp, and no taller than the room left.
              val side =
                  minOf(
                      (constraints.maxWidth * FRAME_WIDTH_FRACTION).roundToInt(),
                      FRAME_MAX_SIZE.roundToPx(),
                      constraints.maxHeight,
                  )
              val frame = measurable.measure(Constraints.fixed(side, side))
              layout(side, side) { frame.place(0, 0) }
            }
            .testTag(ScanScreenTestTags.VIEWFINDER)
            .drawWithCache {
              val scrim = scrimPath(size, size.width * HOLE_RADIUS_RATIO)
              val stroke = CORNER_STROKE.toPx()
              val corners = cornersPath(size, stroke)
              val lineHeight = LINE_HEIGHT.toPx()
              val glow = GLOW_HEIGHT.toPx()
              val dp = 1.dp.toPx()
              // Every animated value is read here, while drawing: a frame of the animation redraws
              // this box only, with no recomposition and no new layout.
              onDrawWithContent {
                drawPath(scrim, SCRIM_COLOR)
                val alpha = lineAlpha.value
                if (alpha > 0f) drawScanLine(sweep.value, primary, alpha, lineHeight, glow)
                withTransform({
                  translate(left = shake.value * dp)
                  scale(scale.value, scale.value, center)
                }) {
                  drawPath(
                      corners,
                      cornerColor.value,
                      style = Stroke(stroke, cap = StrokeCap.Round),
                  )
                }
              }
            }
    )
    Spacer(Modifier.height(24.dp))
    if (animate) {
      AnimatedContent(
          targetState = state,
          transitionSpec = {
            fadeIn(tween(HINT_FADE_MILLIS)) togetherWith fadeOut(tween(HINT_FADE_MILLIS))
          },
          label = "hint",
      ) {
        ViewfinderHint(it)
      }
    } else {
      ViewfinderHint(state)
    }
  }
}

/** What to do, or what happened to the last code, under the frame. */
@Composable
private fun ViewfinderHint(state: ViewfinderState) {
  Text(
      text =
          stringResource(
              when (state) {
                ViewfinderState.SEARCHING -> R.string.scan_hint
                ViewfinderState.FOUND -> R.string.scan_code_read
                ViewfinderState.UNKNOWN -> R.string.scan_invalid_code
              }
          ),
      color = if (state == ViewfinderState.UNKNOWN) shifter_warning else Color.White,
      fontSize = 15.sp,
      textAlign = TextAlign.Center,
      modifier =
          Modifier.fillMaxWidth()
              // Read again when it changes: a code read or refused is said without a touch.
              .semantics { liveRegion = LiveRegionMode.Polite }
              .testTag(
                  if (state == ViewfinderState.UNKNOWN) ScanScreenTestTags.INVALID_CODE
                  else ScanScreenTestTags.HINT
              ),
  )
}

/** Whether the system animations are on: off when "Remove animations" is set, for example. */
@Composable
internal fun rememberAnimationsEnabled(): Boolean {
  val resolver = LocalContext.current.contentResolver
  return remember(resolver) {
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
  }
}

/**
 * The camera darkened around a frame of [size]: a rect far larger than any screen, with the frame
 * cut out. Drawing outside the box is allowed, so it needs no size of the screen.
 */
private fun scrimPath(size: Size, radius: Float) =
    Path().apply {
      fillType = PathFillType.EvenOdd
      addRect(Rect(-SCRIM_REACH, -SCRIM_REACH, size.width + SCRIM_REACH, size.height + SCRIM_REACH))
      addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(radius)))
    }

/**
 * The four rounded corners of the frame, as in the Figma: arms of 40 and a radius of 25.5 in 260.
 */
private fun cornersPath(size: Size, stroke: Float): Path {
  val start = stroke / 2
  val end = size.width - stroke / 2
  val side = end - start
  val arm = side * CORNER_ARM_RATIO
  val r = side * CORNER_RADIUS_RATIO
  return Path().apply {
    moveTo(start, start + arm)
    lineTo(start, start + r)
    arcTo(Rect(start, start, start + 2 * r, start + 2 * r), 180f, 90f, false)
    lineTo(start + arm, start)
    moveTo(end - arm, start)
    lineTo(end - r, start)
    arcTo(Rect(end - 2 * r, start, end, start + 2 * r), 270f, 90f, false)
    lineTo(end, start + arm)
    moveTo(end, end - arm)
    lineTo(end, end - r)
    arcTo(Rect(end - 2 * r, end - 2 * r, end, end), 0f, 90f, false)
    lineTo(end - arm, end)
    moveTo(start + arm, end)
    lineTo(start + r, end)
    arcTo(Rect(start, end - 2 * r, start + 2 * r, end), 90f, 90f, false)
    lineTo(start, end - arm)
  }
}

/**
 * The scan line at [phase] (0 to 1 going down, 1 to 2 going back up), in [color], with a soft glow
 * on the side it comes from. It fades near the edges of its travel.
 */
internal fun DrawScope.drawScanLine(
    phase: Float,
    color: Color,
    alpha: Float,
    lineHeight: Float,
    glow: Float,
) {
  val down = phase < 1f
  val progress = FastOutSlowInEasing.transform(if (down) phase else 2f - phase)
  val left = size.width * LINE_INSET
  val width = size.width - 2 * left
  val y = size.height * (LINE_INSET + progress * (1 - 2 * LINE_INSET))
  val shown = alpha * (0.35f + 0.65f * sin(PI.toFloat() * progress))
  val tint = color.copy(alpha = GLOW_ALPHA)
  val (glowTop, colors) =
      if (down) (y - glow) to listOf(Color.Transparent, tint)
      else y to listOf(tint, Color.Transparent)
  translate(top = glowTop) {
    drawRect(
        Brush.verticalGradient(colors, startY = 0f, endY = glow),
        topLeft = Offset(left, 0f),
        size = Size(width, glow),
        alpha = shown,
    )
  }
  drawRoundRect(
      color,
      topLeft = Offset(left, y - lineHeight / 2),
      size = Size(width, lineHeight),
      cornerRadius = CornerRadius(lineHeight / 2),
      alpha = shown,
  )
}

/** The frame: 265 dp of the 358 dp between the margins of a 390 dp phone, as in the Figma. */
private const val FRAME_WIDTH_FRACTION = 265f / 358f
private val FRAME_MAX_SIZE = 265.dp
private const val HOLE_RADIUS_RATIO = 28f / 265f
internal const val CORNER_ARM_RATIO = 40f / 260f
internal const val CORNER_RADIUS_RATIO = 25.5f / 260f
internal val CORNER_STROKE = 5.dp
private val SCRIM_COLOR = Color(0xCC0B1018)
private const val SCRIM_REACH = 10_000f

/** How far from the frame's edges the scan line stays, as a part of its side. */
internal const val LINE_INSET = 0.08f
private val LINE_HEIGHT = 3.dp
private val GLOW_HEIGHT = 24.dp
private const val GLOW_ALPHA = 0.28f

internal const val SWEEP_MILLIS = 1_800
private const val COLOR_MILLIS = 280
private const val LINE_FADE_MILLIS = 220
private const val HINT_FADE_MILLIS = 180

private const val SHAKE_MILLIS = 360
internal const val FOUND_SCALE = 0.9f
