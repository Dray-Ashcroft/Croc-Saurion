package com.dking.crocapp.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.dking.crocapp.pairing.PanelDiscovery

/**
 * Drop-in screen: searches for a paired panel on the local Wi-Fi and shows
 * [CrocPairingAnimation] while it does. Call [onConnect] once the user taps
 * Connect — hand the resolved host/port off to the existing transfer flow.
 *
 * On the panel side, call `PanelDiscovery(context, panelLabel).startAdvertising(port)`
 * instead of using this screen, so the phone can find it.
 */
@Composable
fun PairingScreen(
    onConnect: (PanelDiscovery.DiscoveredPeer) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val discovery = remember { PanelDiscovery(context, deviceLabel = android.os.Build.MODEL) }
    val state by discovery.state.collectAsState()

    DisposableEffect(Unit) {
        discovery.startDiscovery()
        onDispose { discovery.stopDiscovery() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CrocPairingAnimation(
            state = state,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        val found = state as? PanelDiscovery.State.Found
        Text(
            text = found?.let { "Found ${it.peer.name.removePrefix("crocapp-")}" }
                ?: "Searching for a panel on this Wi-Fi…",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { found?.let { onConnect(it.peer) } },
            enabled = found != null
        ) {
            Text("Connect")
        }
    }
}
