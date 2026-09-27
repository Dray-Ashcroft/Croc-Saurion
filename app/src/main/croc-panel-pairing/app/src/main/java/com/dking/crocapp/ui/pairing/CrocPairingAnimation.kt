package com.dking.crocapp.ui.pairing

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dking.crocapp.R
import com.dking.crocapp.pairing.PanelDiscovery

/**
 * Subtle, minimal loading visual: the crocodile mascot inches toward a
 * "smart panel" icon while searching, then glides the rest of the way in
 * once a peer is found. Meant to sit above a "Searching…" label.
 *
 * Deliberately restrained — a slow creep-and-settle loop, not a bounce or
 * a cartoon run cycle. It should read as "still looking", not distract.
 */
@Composable
fun CrocPairingAnimation(
    state: PanelDiscovery.State,
    modifier: Modifier = Modifier
) {
    val found = state is PanelDiscovery.State.Found
    val infiniteTransition = rememberInfiniteTransition(label = "pairing")

    // While searching: creeps forward, eases back — never quite arrives.
    val searchingProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "searchingProgress"
    )

    // A faint vertical bob so it doesn't read as a static icon while idle-searching.
    val bob by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    // On found: glide the rest of the way in with a soft, single settle-bounce.
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
            .height(72.dp)
    ) {
        val travel = maxWidth - 64.dp // reserve room for both icons at the end

        Icon(
            imageVector = Icons.Outlined.Tv,
            contentDescription = "Smart panel",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(40.dp),
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
                .size(width = 56.dp, height = 32.dp)
                .scale(if (found) arrivalScale else 1f)
        )
    }
}
