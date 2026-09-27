package com.dking.crocapp.pairing

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress

/**
 * Auto-discovery for trusted croc-app devices on the local network, using
 * Android's built-in Network Service Discovery (mDNS/DNS-SD). No Google
 * Play Services required, so it works on unknown-vendor panel firmware too.
 *
 * Pairing is symmetric: one side calls [startAdvertising] to be found,
 * the other calls [startDiscovery] to find it. A phone and a panel can
 * both do both.
 */
class PanelDiscovery(context: Context, private val deviceLabel: String) {

    companion object {
        private const val TAG = "PanelDiscovery"
        private const val SERVICE_TYPE = "_crocapp._tcp."
        private const val SERVICE_NAME_PREFIX = "crocapp-"
    }

    data class DiscoveredPeer(
        val name: String,
        val host: InetAddress,
        val port: Int
    )

    sealed class State {
        data object Idle : State()
        data object Searching : State()
        data class Found(val peer: DiscoveredPeer) : State()
    }

    private val nsdManager = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /** Advertise this device so a peer running [startDiscovery] can find it. */
    fun startAdvertising(port: Int) {
        stopAdvertising()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME_PREFIX + deviceLabel
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Advertising as ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Advertising failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "Stopped advertising")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregister failed: $errorCode")
            }
        }
        registrationListener = listener
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopAdvertising() {
        registrationListener?.let { runCatching { nsdManager.unregisterService(it) } }
        registrationListener = null
    }

    /** Look for another device advertising via [startAdvertising]. */
    fun startDiscovery() {
        stopDiscovery()
        _state.value = State.Searching

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Discovery started")
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                if (!info.serviceName.startsWith(SERVICE_NAME_PREFIX)) return
                Log.i(TAG, "Found ${info.serviceName}, resolving…")
                resolve(info)
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                val current = _state.value
                if (current is State.Found && current.peer.name == info.serviceName) {
                    _state.value = State.Searching
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                _state.value = State.Idle
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun stopDiscovery() {
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        if (_state.value !is State.Idle) _state.value = State.Idle
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        nsdManager.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${info.serviceName}: $errorCode")
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host ?: return
                Log.i(TAG, "Resolved ${info.serviceName} -> $host:${info.port}")
                _state.value = State.Found(
                    DiscoveredPeer(name = info.serviceName, host = host, port = info.port)
                )
            }
        })
    }
}
