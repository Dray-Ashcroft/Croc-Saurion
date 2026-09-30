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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.imageResource
import com.dking.crocapp.R
import kotlin.math.PI
import kotlin.math.sin

/**
 * Renders a [CrocodileRig]'s current joint state using YOUR supplied
 * artwork (the 12 layers cropped from "Rig-Ready Crocodile Animation
 * Sheet.png": rig_body, rig_tail, rig_head, and 8 leg pieces). Nothing here
 * is redrawn or vector-approximated — every part is the original raster PNG,
 * positioned and rotated by real Compose canvas transforms. All animation
 * logic (the sine gait, start/stop, per-joint control) lives in
 * [CrocodileRig] / [rememberCrocodileRig]; this file only draws.
 *
 * Attachment points below were measured directly from the pixel data of
 * each PNG (alpha-weighted centroids at the joint ends, and the opaque
 * Y-range at each seam edge) — not eyeballed. See the anchor constants.
 *
 * `rig_eye.png` (layer 12) is not drawn separately: rig_head.png already
 * has the eye baked into its artwork at the correct spot, so overlaying
 * the standalone eye crop on top would just double-draw the same pixels
 * with a risk of misregistration. If independent eye animation (e.g. a
 * blink) is wanted later, the head art would need the eye cut back out
 * first — it isn't cut out in the layers provided.
 *
 * No separate ankle/foot joint: the supplied lower-leg pieces already
 * include the foot/toes as part of that one image (there's no 13th "foot"
 * layer). "Foot articulation" therefore comes from the knee's asymmetric
 * bend — folding during the forward swing, extending through stance —
 * the same visual cue a third joint would add, without inventing a joint
 * that isn't backed by a separate asset.
 *
 * Draw order: far legs (RearRight, FrontRight) first so the body occludes
 * most of them, then Tail, Body, Head, then near legs (RearLeft, FrontLeft)
 * on top, fully visible — the standard 2D side-on trick for suggesting the
 * far side of the animal. The whole assembly (legs included) is nudged by
 * `rig.bodyBobOffset` together, so nothing detaches at the hip when the
 * body "settles" on each step.
 */

private const val CANVAS_W = 1000f
private const val CANVAS_H = 340f

// Body placed here in the local coordinate space; tail extends left of it,
// head extends right, legs hang below. Never itself rotated or offset
// except for the shared, whole-assembly body-bob translate.
private val BodyTopLeft = Offset(310f, 55f)

// Measured from body.png: alpha-weighted centroid of the belly-line notch
// where each pair of legs sockets in (same point serves the near+far leg
// of that pair — a flat side view only shows one hip position per pair).
private val RearHip = BodyTopLeft + Offset(86f, 87f)
private val FrontHip = BodyTopLeft + Offset(386f, 95f)

// Measured from the opaque Y-range at body's left/right edge columns and
// tail's right edge / head's left edge — the seam pivot is the midpoint of
// that shared edge, so tail and head swing from where they actually meet
// the torso, not from an arbitrary corner.
// Re-measured against the ridge (top edge) at each seam, not the full
// opaque-span midpoint: the ridge line is what a viewer's eye actually
// follows across the join, so that's what has to line up, even if the
// belly line underneath is a little less exact as a result.
private val TailPivot = BodyTopLeft + Offset(0f, 40f)
private val HeadPivot = BodyTopLeft + Offset(439f, 50f)
// +4px overlap on each: the source pieces are measurably different
// thickness right at their seams (tail ~30% thicker than body there, head
// ~40% thinner) — these weren't necessarily drawn to interlock to the
// pixel, so a small overlap hides the residual step instead of chasing an
// exact edge match that may not exist in the source art.
// 4px wasn't enough: the two cut edges meet at the ridge point but aren't
// parallel, so they diverge below it into a wedge-shaped gap toward the
// belly (confirmed by zooming into the actual rendered screenshot — a
// visible triangle of background showing through on both seams). Since
// each piece is drawn after the one it overlaps, more overlap just means
// more of the later piece's own fill covering that wedge — safe, no
// distortion, just needs to be generous enough to actually close it.
private val TailTopLeftRest = TailPivot - Offset(302f - 20f, 14f)
private val HeadTopLeftRest = HeadPivot - Offset(0f + 20f, 33f)

/** Per-leg anchor points, in that leg image's OWN local pixel space (not canvas space). */
private data class LegAnchors(val upperTop: Offset, val upperBottom: Offset, val lowerTop: Offset)

private val FrontLeftAnchors = LegAnchors(Offset(59.6f, 9.1f), Offset(22.0f, 79.1f), Offset(16.0f, 8.7f))
private val FrontRightAnchors = LegAnchors(Offset(58.7f, 8.9f), Offset(21.0f, 79.4f), Offset(16.3f, 9.0f))
private val RearLeftAnchors = LegAnchors(Offset(59.2f, 9.8f), Offset(26.4f, 81.1f), Offset(17.2f, 8.9f))
private val RearRightAnchors = LegAnchors(Offset(63.3f, 9.4f), Offset(29.8f, 77.3f), Offset(16.5f, 9.4f))

private val WaterColor = Color(0xFF6FB8C9)

@Composable
fun CrocodileCanvas(
    rig: CrocodileRig,
    modifier: Modifier = Modifier
) {
    val body = ImageBitmap.imageResource(R.drawable.rig_body)
    val tail = ImageBitmap.imageResource(R.drawable.rig_tail)
    val head = ImageBitmap.imageResource(R.drawable.rig_head)
    val frontLeftUpper = ImageBitmap.imageResource(R.drawable.rig_front_left_upper)
    val frontLeftLower = ImageBitmap.imageResource(R.drawable.rig_front_left_lower)
    val frontRightUpper = ImageBitmap.imageResource(R.drawable.rig_front_right_upper)
    val frontRightLower = ImageBitmap.imageResource(R.drawable.rig_front_right_lower)
    val rearLeftUpper = ImageBitmap.imageResource(R.drawable.rig_rear_left_upper)
    val rearLeftLower = ImageBitmap.imageResource(R.drawable.rig_rear_left_lower)
    val rearRightUpper = ImageBitmap.imageResource(R.drawable.rig_rear_right_upper)
    val rearRightLower = ImageBitmap.imageResource(R.drawable.rig_rear_right_lower)

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

            translate(top = -rig.bodyBobOffset) {
                drawLeg(hipPivot = RearHip, anchors = RearRightAnchors, upper = rearRightUpper, lower = rearRightLower,
                    hipAngleDeg = rig.rearRightHipAngle, kneeAngleDeg = rig.rearRightKneeAngle, far = true)
                drawLeg(hipPivot = FrontHip, anchors = FrontRightAnchors, upper = frontRightUpper, lower = frontRightLower,
                    hipAngleDeg = rig.frontRightHipAngle, kneeAngleDeg = rig.frontRightKneeAngle, far = true)

                rotate(degrees = rig.tailSwayAngle, pivot = TailPivot) {
                    drawImage(tail, topLeft = TailTopLeftRest)
                }
                drawImage(body, topLeft = BodyTopLeft)
                rotate(degrees = rig.headAngle, pivot = HeadPivot) {
                    drawImage(head, topLeft = HeadTopLeftRest)
                }

                drawLeg(hipPivot = RearHip, anchors = RearLeftAnchors, upper = rearLeftUpper, lower = rearLeftLower,
                    hipAngleDeg = rig.rearLeftHipAngle, kneeAngleDeg = rig.rearLeftKneeAngle, far = false)
                drawLeg(hipPivot = FrontHip, anchors = FrontLeftAnchors, upper = frontLeftUpper, lower = frontLeftLower,
                    hipAngleDeg = rig.frontLeftHipAngle, kneeAngleDeg = rig.frontLeftKneeAngle, far = false)
            }
        }
    }
}

/**
 * Draws one leg's upper+lower artwork, hip pivot -> knee pivot, nested so
 * the knee rotation is expressed in the hip's already-rotated local space —
 * that's what keeps the lower piece genuinely attached at the knee no
 * matter what either angle is, rather than two independently-placed images
 * that happen to line up only at rest.
 *
 * [hipPivot] is shared by two legs (see the call sites): a front-pair leg
 * and a rear-pair leg each have one hip/shoulder point on the body, used
 * by both their near and far leg — a flat side view only shows one hip
 * position per pair (see [RearHip] / [FrontHip]).
 */
private fun DrawScope.drawLeg(
    hipPivot: Offset,
    anchors: LegAnchors,
    upper: ImageBitmap,
    lower: ImageBitmap,
    hipAngleDeg: Float,
    kneeAngleDeg: Float,
    far: Boolean
) {
    val legScale = if (far) 0.82f else 1f
    val imgAlpha = if (far) 0.9f else 1f

    scale(legScale, legScale, pivot = hipPivot) {
        rotate(degrees = hipAngleDeg, pivot = hipPivot) {
            drawImage(upper, topLeft = hipPivot - anchors.upperTop, alpha = imgAlpha)

            val kneePivot = hipPivot + (anchors.upperBottom - anchors.upperTop)
            rotate(degrees = kneeAngleDeg, pivot = kneePivot) {
                drawImage(lower, topLeft = kneePivot - anchors.lowerTop, alpha = imgAlpha)
            }
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
            x += 8f
        }
        return path
    }
    val centerY = BodyTopLeft.y + 107f + 90f // roughly below where feet reach
    drawPath(
        path = wave(baseY = centerY, freq = 140f, speed = 1f, phaseOffset = 0f),
        color = WaterColor.copy(alpha = alpha),
        style = Stroke(width = 2.4f, cap = StrokeCap.Round)
    )
    drawPath(
        path = wave(baseY = centerY + 14f, freq = 100f, speed = -0.7f, phaseOffset = 0.35f),
        color = WaterColor.copy(alpha = alpha * 0.8f),
        style = Stroke(width = 2f, cap = StrokeCap.Round)
    )
}
