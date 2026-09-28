package com.dking.crocapp.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dking.crocapp.R

/**
 * Tiny crocodile that rides along above a progress bar at [progress] (0f..1f).
 * Purely decorative: draws nothing interactive and never affects the bar itself.
 */
@Composable
fun CrocProgressMarker(progress: Float, modifier: Modifier = Modifier) {
    val p = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(20.dp)) {
        val markerWidth = 30.dp
        val travel = (maxWidth - markerWidth).coerceAtLeast(0.dp)
        Image(
            painter = painterResource(R.drawable.croc_mascot),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = travel * p)
                .size(width = markerWidth, height = 18.dp)
        )
    }
}
