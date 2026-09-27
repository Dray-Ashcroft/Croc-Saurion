package com.dking.crocapp.ui.pairing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dking.crocapp.R
import com.dking.crocapp.croc.CrocTransferState
import kotlinx.coroutines.delay

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
    LaunchedEffect(isPairing, justBound) {
        when {
            isPairing -> {
                mounted = true
                visible = true
            }
            justBound -> {
                mounted = true
                visible = true
                delay(700)
                visible = false
                delay(350)
                mounted = false
            }
            else -> {
                visible = false
                mounted = false
            }
        }
    }

    if (!mounted) return

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(350)) + shrinkVertically(tween(350))
    ) {
        val found = justBound
        val infiniteTransition = rememberInfiniteTransition(label = "pairing")

        val searchingProgress by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "searchingProgress"
        )

        val bob by infiniteTransition.animateFloat(
            initialValue = -3f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bob"
        )

        val progress by animateFloatAsState(
            targetValue = if (found) 1f else searchingProgress,
            animationSpec = if (found) {
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            } else {
                snap()
            },
            label = "progress"
        )

        val arrivalScale by animateFloatAsState(
            targetValue = if (found) 1.08f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "arrivalScale"
        )

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            val travel = maxWidth - 56.dp

            Icon(
                imageVector = Icons.Outlined.Tv,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(32.dp),
                tint = if (found) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Image(
                painter = painterResource(R.drawable.croc_mascot),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = travel * progress, y = (if (found) 0f else bob).dp)
                    .size(width = 48.dp, height = 28.dp)
                    .scale(if (found) arrivalScale else 1f)
            )
        }
    }
}
