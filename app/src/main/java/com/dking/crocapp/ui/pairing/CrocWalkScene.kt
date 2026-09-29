package com.dking.crocapp.ui.pairing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * A small, self-contained vector crocodile: no bitmap assets. Every part is
 * a hand-built [Path] in a fixed 240x110 local coordinate space, scaled to
 * fit whatever size the caller gives it.
 *
 * Rig, not a literal trace of any reference image: body+head, a separately
 * rotatable tail (split at a seam so it can sway on its own pivot), and
 * four independently rotatable legs (near-front, near-rear drawn in front
 * of the body; far-front, far-rear drawn behind it so they read as the
 * opposite side of the animal, partly occluded — the usual 2D side-on
 * quadruped trick).
 *
 * [activity] is 0f..1f and scales every motion's amplitude — legs, tail,
 * body bob, and (partly) the water ripple. At activity=0 everything is
 * fully at rest but the composable keeps recomposing (ripple keeps a
 * little life), which is what "settled" should look like: still, not
 * frozen mid-stride.
 */

private val BodyGreen = Color(0xFF3F7D50)
private val DarkGreen = Color(0xFF16583F)
private val Cream = Color(0xFFD8E0B0)
private val EyeWhite = Color(0xFFF4F7EC)
private val PupilDark = Color(0xFF16301F)
private val WaterColor = Color(0xFF6FB8C9)

private const val CANVAS_W = 240f
private const val CANVAS_H = 110f

// Seam where the tail is cut from the torso, so it can rotate independently.
private val SeamTop = Offset(34f, 24f)
private val SeamBottom = Offset(40f, 44f)
private val TailPivot = Offset(37f, 34f)

private val RearHip = Offset(70f, 44f)
private val FrontShoulder = Offset(168f, 40f)
private val RearHipFar = Offset(64f, 40f)
private val FrontShoulderFar = Offset(162f, 36f)

@Composable
fun CrocWalkScene(
    activity: Float,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "crocWalk")

    // One full stride cycle. Restart (not Reverse) so the gait always runs
    // forward — a walk cycle looping is meant to look continuous, not ping-pong.
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val ripplePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripplePhase"
    )

    val a = activity.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier.aspectRatio(240f / 110f)
    ) {
        val sx = size.width / CANVAS_W
        val sy = size.height / CANVAS_H
        scale(scaleX = sx, scaleY = sy, pivot = Offset.Zero) {
            drawWaterRipple(ripplePhase, a)

            val bob = a * bobOffset(phase)
            translate(top = -bob) {
                drawLeg(RearHipFar, legAngle(phase, 0.5f, a), a, far = true)
                drawLeg(FrontShoulderFar, legAngle(phase, 0f, a), a, far = true)

                drawTail(tailSway(phase, a))
                drawTorsoAndHead()

                drawLeg(RearHip, legAngle(phase, 0.5f, a), a, far = false)
                drawLeg(FrontShoulder, legAngle(phase, 0f, a), a, far = false)
            }
        }
    }
}

/** -1..1 swing; positive = leg forward. [offset] staggers the diagonal pair. */
private fun swing(phase: Float, offset: Float): Float =
    sin(2f * PI.toFloat() * (phase + offset))

private fun legAngle(phase: Float, offset: Float, activity: Float): Float {
    val s = swing(phase, offset)
    val baseAmplitudeDeg = 24f
    return activity * baseAmplitudeDeg * s
}

/** Two footfalls per stride cycle, always >= 0 — a body settles down on each step, not up. */
private fun bobOffset(phase: Float): Float {
    val cyc = 4f * PI.toFloat() * phase
    return 1.4f * (0.5f - 0.5f * kotlin.math.cos(cyc))
}

private fun tailSway(phase: Float, activity: Float): Float =
    activity * 5f * sin(2f * PI.toFloat() * phase + PI.toFloat() / 2f)

private fun DrawScope.drawLeg(pivot: Offset, angleDeg: Float, activity: Float, far: Boolean) {
    // A small extra upward hop while the leg is in the forward half of its swing (angleDeg > 0),
    // so it reads as a lifted step rather than the whole leg sliding through the ground plane.
    val forwardFraction = max(0f, angleDeg / 24f) // 0..1, independent of `activity`'s own scaling above
    val lift = activity * 3f * sin(PI.toFloat() * forwardFraction)

    val legScale = if (far) 0.82f else 1f
    val color = if (far) DarkGreen.copy(alpha = 0.9f) else DarkGreen

    translate(top = -lift) {
        rotate(degrees = angleDeg, pivot = pivot) {
            scale(legScale, legScale, pivot = pivot) {
                val path = Path().apply {
                    val (x, y) = pivot
                    moveTo(x - 8f, y)
                    lineTo(x + 8f, y)
                    lineTo(x + 9f, y + 26f)
                    lineTo(x + 9f, y + 29f)
                    lineTo(x + 6f, y + 34f)
                    lineTo(x + 3f, y + 29f)
                    lineTo(x, y + 34f)
                    lineTo(x - 3f, y + 29f)
                    lineTo(x - 5f, y + 34f)
                    lineTo(x - 9f, y + 29f)
                    lineTo(x - 9f, y + 26f)
                    close()
                }
                drawPath(path, color = color)
            }
        }
    }
}

private fun DrawScope.drawTail(swayDeg: Float) {
    rotate(degrees = swayDeg, pivot = TailPivot) {
        val path = Path().apply {
            moveTo(8f, 58f)
            cubicTo(10f, 46f, 12f, 30f, 30f, 16f)
            lineTo(SeamTop.x, SeamTop.y)
            lineTo(SeamBottom.x, SeamBottom.y)
            cubicTo(30f, 40f, 18f, 48f, 8f, 58f)
            close()
        }
        drawPath(path, color = BodyGreen)
    }
}

private fun DrawScope.drawTorsoAndHead() {
    val ridgeXs = floatArrayOf(34f, 55f, 75f, 95f, 115f, 135f, 150f)

    val body = Path().apply {
        moveTo(SeamTop.x, SeamTop.y)
        lineTo(55f, 22f)
        lineTo(75f, 20f)
        lineTo(95f, 19f)
        lineTo(115f, 18f)
        lineTo(135f, 18f)
        lineTo(150f, 18f)
        cubicTo(160f, 12f, 168f, 9f, 176f, 10f)
        cubicTo(196f, 11f, 214f, 13f, 224f, 16f)
        lineTo(238f, 24f)
        lineTo(224f, 32f)
        lineTo(210f, 33.5f)
        lineTo(196f, 38f)
        cubicTo(180f, 41f, 165f, 43f, 150f, 44f)
        lineTo(90f, 46f)
        lineTo(SeamBottom.x, SeamBottom.y)
        close()
    }
    drawPath(body, color = BodyGreen)

    // back spikes, sitting on the ridge
    for (bx in ridgeXs) {
        val spike = Path().apply {
            moveTo(bx - 6f, 21f)
            lineTo(bx + 3f, 9f)
            lineTo(bx + 9f, 21f)
            close()
        }
        drawPath(spike, color = DarkGreen)
    }

    // cream flank / jaw band, inset just above the belly edge
    val creamTop = listOf(196f to 29f, 150f to 35f, 90f to 37f, SeamBottom.x to 35f)
    val creamBottom = listOf(SeamBottom.x to SeamBottom.y, 90f to 46f, 150f to 44f, 196f to 38f)
    val cream = Path().apply {
        moveTo(creamTop[0].first, creamTop[0].second)
        for (i in 1 until creamTop.size) lineTo(creamTop[i].first, creamTop[i].second)
        for (i in creamBottom.indices) lineTo(creamBottom[i].first, creamBottom[i].second)
        close()
    }
    drawPath(cream, color = Cream)

    // eye
    drawCircle(color = EyeWhite, radius = 6f, center = Offset(196f, 20f))
    drawCircle(color = PupilDark, radius = 3f, center = Offset(199f, 20f))

    // nostril
    drawCircle(color = PupilDark, radius = 2f, center = Offset(233f, 20f))

    // teeth
    val teethX = floatArrayOf(203f, 211f, 219f)
    for (tx in teethX) {
        val tooth = Path().apply {
            moveTo(tx - 3f, 33f)
            lineTo(tx + 3f, 33f)
            lineTo(tx, 39f)
            close()
        }
        drawPath(tooth, color = EyeWhite)
    }
}

private fun DrawScope.drawWaterRipple(ripplePhase: Float, activity: Float) {
    val amp = 1.2f + 0.9f * activity
    val alpha = 0.14f + 0.10f * activity
    fun wave(baseY: Float, freq: Float, speed: Float, phaseOffset: Float): Path {
        val path = Path()
        var first = true
        var x = -4f
        while (x <= CANVAS_W + 4f) {
            val y = baseY + amp * sin(2f * PI.toFloat() * (x / freq + ripplePhase * speed + phaseOffset))
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            x += 6f
        }
        return path
    }
    drawPath(
        path = wave(baseY = 96f, freq = 70f, speed = 1f, phaseOffset = 0f),
        color = WaterColor.copy(alpha = alpha),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6f, cap = StrokeCap.Round)
    )
    drawPath(
        path = wave(baseY = 101f, freq = 54f, speed = -0.7f, phaseOffset = 0.35f),
        color = WaterColor.copy(alpha = alpha * 0.8f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.3f, cap = StrokeCap.Round)
    )
}
