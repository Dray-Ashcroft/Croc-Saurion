package com.dking.crocapp.ui.pairing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Programmatic control surface + joint state for the rig [CrocodileCanvas]
 * draws. This class holds state only; it never draws anything itself, so it
 * has no Compose UI dependency beyond the state types.
 *
 * Hierarchy:
 * ```
 * Crocodile
 * ├── Body                         (root; fixed — never moves)
 * ├── Head                         (headAngle, about the neck seam)
 * ├── Tail                         (tailSwayAngle, about the tail seam)
 * ├── FrontLeft  { hip → knee → ankle/foot }
 * ├── FrontRight { hip → knee → ankle/foot }
 * ├── RearLeft   { hip → knee → ankle/foot }
 * └── RearRight  { hip → knee → ankle/foot }
 * ```
 * Every hip and knee angle is an independent field — nothing forces the
 * upper and lower segments of a leg to move together; [CrocodileCanvas]
 * renders the knee as a child rotation nested inside the hip's rotation, so
 * they compose correctly (the shin stays attached at the knee no matter
 * what either angle is) without being the same value.
 *
 * While [walking] is true, [rememberCrocodileRig]'s driver overwrites all 8
 * hip/knee angles every frame using `sin(time * speed + phase) * amplitude`
 * — one sine per leg, four phase offsets, diagonal pairs sharing a phase.
 * Set `walking = false` (via [setWalking]) to stop the driver and pose any
 * joint by hand with its setter; the driver never touches [headAngle], so a
 * head-turn pose always stays under manual control even while walking.
 */
class CrocodileRig {

    // ---- global procedural controls ---------------------------------
    // These must be Compose state, not plain vars: setWalking() is called
    // from a *different* composable (whatever hosts this rig) than the one
    // holding `LaunchedEffect(rig.walking)` below. A plain var mutation
    // from outside would never trigger that composable to recompose, so
    // the key would never be seen as changed and the driver would never
    // actually start or stop.
    var walking: Boolean by mutableStateOf(false)
        private set
    var walkSpeed: Float by mutableFloatStateOf(1f)
        private set
    var legAmplitude: Float by mutableFloatStateOf(20f)
        private set
    var kneeBendAmplitude: Float by mutableFloatStateOf(34f)
        private set
    var direction: Float by mutableFloatStateOf(1f) // 1f = forward, -1f = gait runs in reverse
        private set

    fun setWalking(enabled: Boolean) { walking = enabled }
    fun setWalkSpeed(speed: Float) { walkSpeed = speed }
    fun setLegAmplitude(degrees: Float) { legAmplitude = degrees }
    fun setKneeBendAmplitude(degrees: Float) { kneeBendAmplitude = degrees }
    fun setDirection(forward: Boolean) { direction = if (forward) 1f else -1f }

    // ---- per-joint angles, degrees — each is its own Compose state so ---
    // ---- CrocodileCanvas's draw phase can read them independently -------
    var frontLeftHipAngle by mutableFloatStateOf(0f)
        private set
    var frontLeftKneeAngle by mutableFloatStateOf(0f)
        private set
    var frontRightHipAngle by mutableFloatStateOf(0f)
        private set
    var frontRightKneeAngle by mutableFloatStateOf(0f)
        private set
    var rearLeftHipAngle by mutableFloatStateOf(0f)
        private set
    var rearLeftKneeAngle by mutableFloatStateOf(0f)
        private set
    var rearRightHipAngle by mutableFloatStateOf(0f)
        private set
    var rearRightKneeAngle by mutableFloatStateOf(0f)
        private set

    var headAngle by mutableFloatStateOf(0f)
        private set
    var tailSwayAngle by mutableFloatStateOf(0f)
        private set

    fun setFrontLeftHipAngle(degrees: Float) { frontLeftHipAngle = degrees }
    fun setFrontLeftKneeAngle(degrees: Float) { frontLeftKneeAngle = degrees }
    fun setFrontRightHipAngle(degrees: Float) { frontRightHipAngle = degrees }
    fun setFrontRightKneeAngle(degrees: Float) { frontRightKneeAngle = degrees }
    fun setRearLeftHipAngle(degrees: Float) { rearLeftHipAngle = degrees }
    fun setRearLeftKneeAngle(degrees: Float) { rearLeftKneeAngle = degrees }
    fun setRearRightHipAngle(degrees: Float) { rearRightHipAngle = degrees }
    fun setRearRightKneeAngle(degrees: Float) { rearRightKneeAngle = degrees }
    fun setHeadAngle(degrees: Float) { headAngle = degrees }
    fun setTailSwayAngle(degrees: Float) { tailSwayAngle = degrees }

    /** Seconds, advances only while [walking]; persists across stop/start so a resumed walk doesn't jump. */
    internal var timeSeconds: Float = 0f
}

/** Creates a [CrocodileRig] and starts the frame driver that runs while `rig.walking` is true. */
@Composable
fun rememberCrocodileRig(): CrocodileRig {
    val rig = remember { CrocodileRig() }

    LaunchedEffect(rig.walking) {
        if (!rig.walking) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (true) {
            withFrameNanos { nanos ->
                if (lastFrameNanos != 0L) {
                    val dtSeconds = (nanos - lastFrameNanos) / 1_000_000_000f
                    rig.timeSeconds += dtSeconds * rig.direction
                }
                lastFrameNanos = nanos
            }
            applyGait(rig)
        }
    }

    return rig
}

/**
 * legAngle = sin(time * speed + phase) * amplitude, exactly as requested —
 * one call per leg, four phase offsets. FrontLeft and RearRight share a
 * phase (diagonal pair 1); FrontRight and RearLeft share the opposite
 * phase (diagonal pair 2) — a front leg always moves with the
 * *opposite-side* rear leg, which is what makes it a trot instead of a
 * front-pair/rear-pair bound.
 */
private fun applyGait(rig: CrocodileRig) {
    val t = rig.timeSeconds * rig.walkSpeed * TWO_PI

    fun hipAngle(phase: Float) = rig.legAmplitude * sin(t + phase)

    // Knee bend is asymmetric on purpose: it folds during the forward half
    // of the swing (lifting the foot) and stays close to straight through
    // the backward/stance half. A knee that just mirrored the hip's sine
    // 1:1 would look like a second pendulum, not a step.
    fun kneeAngle(phase: Float): Float {
        val forward = smoothstep(max(0f, sin(t + phase)))
        return 6f + rig.kneeBendAmplitude * forward
    }

    val phaseA = 0f
    val phaseB = PI.toFloat()

    rig.setFrontLeftHipAngle(hipAngle(phaseA))
    rig.setFrontLeftKneeAngle(kneeAngle(phaseA))
    rig.setRearRightHipAngle(hipAngle(phaseA))
    rig.setRearRightKneeAngle(kneeAngle(phaseA))

    rig.setFrontRightHipAngle(hipAngle(phaseB))
    rig.setFrontRightKneeAngle(kneeAngle(phaseB))
    rig.setRearLeftHipAngle(hipAngle(phaseB))
    rig.setRearLeftKneeAngle(kneeAngle(phaseB))

    // Light secondary motion. Deliberately NOT touching the body/root, and
    // deliberately not touching headAngle — only these two child nodes.
    rig.setTailSwayAngle(4f * sin(t * 0.5f + PI.toFloat() / 2f))
}

private const val TWO_PI = (2.0 * PI).toFloat()

internal fun smoothstep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}
