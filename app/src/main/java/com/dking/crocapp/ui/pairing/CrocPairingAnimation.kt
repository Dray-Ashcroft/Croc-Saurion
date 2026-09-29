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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dking.crocapp.croc.CrocTransferState
import kotlinx.coroutines.delay

/**
 * Drives [CrocWalkScene] from the REAL transfer state — [CrocTransferState],
 * the same state `CrocProcess` publishes and every screen already collects.
 * Nothing here talks to the network, to CrocProcess, or to any separate
 * discovery mechanism; it only reads the state it's given.
 *
 * Behavior, matching the four pairing moments this is meant to cover:
 *  - Preparing / WaitingForPeer -> the scene mounts (fade + subtle scale in)
 *    and [CrocWalkScene] walks continuously in place.
 *  - Transferring -> the walk cycle eases to a stop over ~500ms (a "settle",
 *    not an abrupt freeze), holds briefly, then the whole thing fades out
 *    and unmounts, handing off to the normal progress UI untouched.
 *  - Idle / Completed / StoreCompleted / Error / Cancelled / LegacyFallback
 *    -> stops cleanly: a quick plain fade out, no settle flourish, then
 *    unmounts. (Reached on failure/cancel, and on any other non-pairing
 *    state.)
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

    // `mounted` controls whether this composable occupies a slot in the host
    // Column at all. It must go false only once the exit animation has had
    // time to finish — a collapsed-but-still-mounted AnimatedVisibility would
    // otherwise keep consuming a slot from the host's Arrangement.spacedBy
    // for the rest of a (possibly minutes-long) transfer, leaving a
    // permanent empty gap above the progress UI.
    var mounted by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    // Leg/tail/bob amplitude, separate from `visible`: this is what lets the
    // walk cycle ease to a stop over a few hundred ms instead of freezing
    // mid-stride the instant Transferring arrives.
    val activityTarget = if (isPairing) 1f else 0f
    val activity by animateFloatAsState(
        targetValue = activityTarget,
        animationSpec = if (isPairing) {
            tween(220) // ramping up into a walk should be quick, not sluggish
        } else {
            tween(500) // easing out is the "very subtle settling motion"
        },
        label = "crocActivity"
    )

    LaunchedEffect(isPairing, justBound) {
        when {
            isPairing -> {
                mounted = true
                visible = true
            }
            justBound -> {
                // Let the legs finish easing to a stop (see `activity` above)
                // before the scene fades away.
                mounted = true
                visible = true
                delay(550)
                visible = false
                delay(350)
                mounted = false
            }
            else -> {
                // Fail/cancel/idle/completed: stop cleanly — a quick fade,
                // no settle motion — rather than a hard, jarring pop.
                mounted = true
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
        // Fixed height, no fillMaxWidth: CrocWalkScene derives its own width
        // from this height via its internal aspectRatio, giving a small,
        // compact vignette rather than a drawing stretched to card width.
        CrocWalkScene(
            activity = activity,
            modifier = modifier.height(48.dp)
        )
    }
}
