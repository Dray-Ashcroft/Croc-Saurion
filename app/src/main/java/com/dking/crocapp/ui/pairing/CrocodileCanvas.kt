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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.sin

/**
 * Renders a [CrocodileRig]'s current joint state. This file only draws —
 * all animation logic (the sine gait, start/stop, per-joint control) lives
 * in [CrocodileRig] / [rememberCrocodileRig]. No bitmap assets: every part
 * is a hand-built [Path] in a fixed 240x110 local coordinate space, scaled
 * to whatever size the caller gives it.
 *
 * Draw order matters here and encodes the rig hierarchy: the two "far"
 * (opposite-side) legs are drawn first so the body silhouette occludes
 * most of them, then Tail, then Body (torso), then Head, then the two
 * "near" legs on top, fully visible. That's the standard 2D side-on
 * quadruped trick for suggesting the far side of the animal without a
 * third dimension. The body itself never moves — only its child parts
 * (head, tail, legs) rotate about their own pivots, which is what
 * "the body is the stationary root" means in practice.
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
private val TailSeamTop = Offset(34f, 24f)
private val TailSeamBottom = Offset(40f, 44f)
private val TailPivot = Offset(37f, 34f)

// Seam where the head is cut from the torso (both points are real vertices
// already used by the torso's own outline, so the two paths share an edge
// exactly — no gap, no overlap — when headAngle is 0).
private val HeadSeamTop = Offset(176f, 10f)
private val HeadSeamBottom = Offset(196f, 38f)
private val HeadPivot = Offset(186f, 24f)

// Hip/shoulder pivots, on the torso. "Near" legs (FrontLeft, RearLeft) are
// drawn in front, fully visible; "far" legs (FrontRight, RearRight) are
// drawn behind the body, smaller, mostly occluded.
private val FrontLeftHip = Offset(168f, 40f)
private val RearLeftHip = Offset(70f, 44f)
private val FrontRightHip = Offset(162f, 36f)
private val RearRightHip = Offset(64f, 40f)

@Composable
fun CrocodileCanvas(
    rig: CrocodileRig,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "waterRipple")
    val ripplePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripplePhase"
    )

    Canvas(modifier = modifier.aspectRatio(CANVAS_W / CANVAS_H)) {
        val sx = size.width / CANVAS_W
        val sy = size.height / CANVAS_H
        scale(scaleX = sx, scaleY = sy, pivot = Offset.Zero) {
            drawWaterRipple(ripplePhase, active = rig.walking)

            drawLeg(RearRightHip, rig.rearRightHipAngle, rig.rearRightKneeAngle, far = true)
            drawLeg(FrontRightHip, rig.frontRightHipAngle, rig.frontRightKneeAngle, far = true)

            drawTail(rig.tailSwayAngle)
            drawTorso()
            drawHead(rig.headAngle)

            drawLeg(RearLeftHip, rig.rearLeftHipAngle, rig.rearLeftKneeAngle, far = false)
            drawLeg(FrontLeftHip, rig.frontLeftHipAngle, rig.frontLeftKneeAngle, far = false)
        }
    }
}

private fun DrawScope.drawLeg(pivot: Offset, hipAngleDeg: Float, kneeAngleDeg: Float, far: Boolean) {
    // The foot partly counter-rotates against the shin, so it stays closer to level with
    // the ground instead of swinging like a rigid club nailed to the end of the leg.
    val footAngleDeg = -(hipAngleDeg + kneeAngleDeg) * 0.55f

    val legScale = if (far) 0.82f else 1f
    val color = if (far) DarkGreen.copy(alpha = 0.9f) else DarkGreen
    val thighLen = 13f
    val shinLen = 11f

    scale(legScale, legScale, pivot = pivot) {
        // Hip/shoulder joint: rotating here moves the thigh AND everything nested inside it.
        rotate(degrees = hipAngleDeg, pivot = pivot) {
            drawSegment(pivot, halfWidthTop = 7f, halfWidthBottom = 6f, length = thighLen, color = color)

            // Knee joint, nested inside the hip's rotation: its pivot is expressed in the
            // hip's already-rotated local space, so it automatically stays attached at the
            // knee no matter what hipAngleDeg is — the whole point of a real joint chain
            // instead of one rigid leg.
            val knee = Offset(pivot.x, pivot.y + thighLen)
            rotate(degrees = kneeAngleDeg, pivot = knee) {
                drawSegment(knee, halfWidthTop = 6f, halfWidthBottom = 5f, length = shinLen, color = color)

                val ankle = Offset(knee.x, knee.y + shinLen)
                rotate(degrees = footAngleDeg, pivot = ankle) {
                    drawFoot(ankle, color)
                }
            }
        }
    }
}

/** One tapered leg segment (thigh or shin), drawn straight down from [top] in the current (already-rotated) space. */
private fun DrawScope.drawSegment(top: Offset, halfWidthTop: Float, halfWidthBottom: Float, length: Float, color: Color) {
    val path = Path().apply {
        moveTo(top.x - halfWidthTop, top.y)
        lineTo(top.x + halfWidthTop, top.y)
        lineTo(top.x + halfWidthBottom, top.y + length)
        lineTo(top.x - halfWidthBottom, top.y + length)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawFoot(top: Offset, color: Color) {
    val x = top.x
    val y = top.y
    val path = Path().apply {
        moveTo(x - 6f, y)
        lineTo(x + 6f, y)
        lineTo(x + 6f, y + 3f)
        lineTo(x + 4f, y + 7f)
        lineTo(x + 2f, y + 3f)
        lineTo(x, y + 7f)
        lineTo(x - 2f, y + 3f)
        lineTo(x - 4f, y + 7f)
        lineTo(x - 6f, y + 3f)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawTail(swayDeg: Float) {
    rotate(degrees = swayDeg, pivot = TailPivot) {
        val path = Path().apply {
            moveTo(8f, 58f)
            cubicTo(10f, 46f, 12f, 30f, 30f, 16f)
            lineTo(TailSeamTop.x, TailSeamTop.y)
            lineTo(TailSeamBottom.x, TailSeamBottom.y)
            cubicTo(30f, 40f, 18f, 48f, 8f, 58f)
            close()
        }
        drawPath(path, color = BodyGreen)
    }
}

/** The torso: back ridge, spikes, flank/cream band. Root of the rig — never rotated, never translated. */
private fun DrawScope.drawTorso() {
    val ridgeXs = floatArrayOf(34f, 55f, 75f, 95f, 115f, 135f, 150f)

    val torso = Path().apply {
        moveTo(TailSeamTop.x, TailSeamTop.y)
        lineTo(55f, 22f)
        lineTo(75f, 20f)
        lineTo(95f, 19f)
        lineTo(115f, 18f)
        lineTo(135f, 18f)
        lineTo(150f, 18f)
        cubicTo(160f, 12f, 168f, 9f, HeadSeamTop.x, HeadSeamTop.y)
        lineTo(HeadSeamBottom.x, HeadSeamBottom.y) // straight cut to the head seam
        cubicTo(180f, 41f, 165f, 43f, 150f, 44f)
        lineTo(90f, 46f)
        lineTo(TailSeamBottom.x, TailSeamBottom.y)
        close()
    }
    drawPath(torso, color = BodyGreen)

    for (bx in ridgeXs) {
        val spike = Path().apply {
            moveTo(bx - 6f, 21f)
            lineTo(bx + 3f, 9f)
            lineTo(bx + 9f, 21f)
            close()
        }
        drawPath(spike, color = DarkGreen)
    }

    val creamTop = listOf(196f to 29f, 150f to 35f, 90f to 37f, TailSeamBottom.x to 35f)
    val creamBottom = listOf(TailSeamBottom.x to TailSeamBottom.y, 90f to 46f, 150f to 44f, 196f to 38f)
    val cream = Path().apply {
        moveTo(creamTop[0].first, creamTop[0].second)
        for (i in 1 until creamTop.size) lineTo(creamTop[i].first, creamTop[i].second)
        for (i in creamBottom.indices) lineTo(creamBottom[i].first, creamBottom[i].second)
        close()
    }
    drawPath(cream, color = Cream)
}

/** The head: snout, jaw, eye, nostril, teeth. Rotates about [HeadPivot] (the neck joint). */
private fun DrawScope.drawHead(headAngleDeg: Float) {
    rotate(degrees = headAngleDeg, pivot = HeadPivot) {
        val head = Path().apply {
            moveTo(HeadSeamTop.x, HeadSeamTop.y)
            cubicTo(196f, 11f, 214f, 13f, 224f, 16f)
            lineTo(238f, 24f) // snout tip
            lineTo(224f, 32f)
            lineTo(210f, 33.5f)
            lineTo(HeadSeamBottom.x, HeadSeamBottom.y)
            lineTo(HeadSeamTop.x, HeadSeamTop.y) // straight cut back to the torso seam
            close()
        }
        drawPath(head, color = BodyGreen)

        drawCircle(color = EyeWhite, radius = 6f, center = Offset(196f, 20f))
        drawCircle(color = PupilDark, radius = 3f, center = Offset(199f, 20f))
        drawCircle(color = PupilDark, radius = 2f, center = Offset(233f, 20f)) // nostril

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
}

private fun DrawScope.drawWaterRipple(ripplePhase: Float, active: Boolean) {
    val amp = if (active) 2.1f else 1.2f
    val alpha = if (active) 0.24f else 0.14f
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
        style = Stroke(width = 1.6f, cap = StrokeCap.Round)
    )
    drawPath(
        path = wave(baseY = 101f, freq = 54f, speed = -0.7f, phaseOffset = 0.35f),
        color = WaterColor.copy(alpha = alpha * 0.8f),
        style = Stroke(width = 1.3f, cap = StrokeCap.Round)
    )
}
