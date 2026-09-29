package com.dking.crocapp.ui.pairing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dking.crocapp.croc.CrocTransferState
import kotlinx.coroutines.delay

/**
 * Drives [CrocodileRig] (and therefore [CrocodileCanvas]) from the REAL
 * transfer state — [CrocTransferState], the same state `CrocProcess`
 * publishes and every screen already collects. Nothing here talks to the
 * network, to CrocProcess, or to any separate discovery mechanism; it only
 * reads the state it's given.
 *
 * Behavior:
 *  - Preparing / WaitingForPeer -> the scene mounts (fade + subtle scale in)
 *    and the rig walks continuously (`rig.setWalking(true)`).
 *  - Transferring -> leg swing/knee-bend amplitude eases to 0 over ~500ms
 *    (the "settle" — legs slow to a stop rather than freezing mid-stride),
 *    then `setWalking(false)`, holds briefly, then the whole thing fades
 *    out and unmounts, handing off to the normal progress UI untouched.
 *  - Idle / Completed / StoreCompleted / Error / Cancelled / LegacyFallback
 *    -> stops cleanly: a quick plain fade out, no settle flourish.
 *
 * Renders nothing (zero height, not even a mounted-but-invisible node) once
 * stopped, so it never leaves a stray gap in a host Column that uses
 * Arrangement.spacedBy — see the comment on `mounted` below.
 */
@Composable
fun CrocPairingAnimation(
    state: CrocTransferState,
    modifier: Modifier = Modifier
) {
    val isPairing = state is CrocTransferState.Preparing ||
        state is CrocTransferState.WaitingForPeer
    val justBound = state is CrocTransferState.Transferring

    var mounted by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    val rig = rememberCrocodileRig()

    // Amplitude, separate from `visible`/`walking`: this is what lets the
    // walk cycle ease to a stop over a few hundred ms instead of the legs
    // freezing mid-stride the instant Transferring arrives.
    val amplitude by animateFloatAsState(
        targetValue = if (isPairing) 1f else 0f,
        animationSpec = if (isPairing) tween(220) else tween(500),
        label = "crocAmplitude"
    )
    SideEffect {
        rig.setLegAmplitude(20f * amplitude)
        rig.setKneeBendAmplitude(34f * amplitude)
    }

    LaunchedEffect(isPairing, justBound) {
        when {
            isPairing -> {
                mounted = true
                visible = true
                rig.setWalking(true)
            }
            justBound -> {
                // Let the legs finish easing to a stop (see `amplitude` above,
                // driven by the same 500ms tween) before stopping the driver
                // and fading the scene away.
                mounted = true
                visible = true
                delay(500)
                rig.setWalking(false)
                delay(50)
                visible = false
                delay(350)
                mounted = false
            }
            else -> {
                // Fail/cancel/idle/completed: stop cleanly — a quick fade,
                // no settle motion — rather than a hard, jarring pop.
                mounted = true
                rig.setWalking(false)
                visible = false
                delay(180)
                mounted = false
            }
        }
    }

    if (!mounted) return

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280)) + scaleIn(
            initialScale = 0.86f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
        ),
        exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
    ) {
        CrocodileCanvas(
            rig = rig,
            modifier = modifier.height(48.dp)
        )
    }
}
