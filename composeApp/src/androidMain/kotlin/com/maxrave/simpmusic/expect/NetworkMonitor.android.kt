package com.maxrave.simpmusic.expect

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Fork: Android's own answer to "is there a network right now".
 *
 * `registerDefaultNetworkCallback` reports both the current state and every later change, which is
 * exactly what the offline mode needs — it is entered when the callback says there is no network and
 * left again the moment one comes back. A ten-second recheck covers devices/ROMs whose callbacks or
 * validation state lag behind an actually usable connection.
 *
 * A network is counted as usable when it reports INTERNET. VALIDATED is deliberately not required:
 * some VPN setups and ROMs leave a working default network unvalidated, which previously froze this
 * tab offline until it was reopened.
 */
@Composable
actual fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val flow = remember(context) { connectivityFlow(context) }
    return flow.collectAsState(initial = currentOnline(context))
}

private fun connectivityFlow(context: Context) = callbackFlow {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (manager == null) {
        trySend(true)
        awaitClose { }
        return@callbackFlow
    }

    trySend(isOnline(manager))

    val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnline(manager))
            }

            override fun onLost(network: Network) {
                trySend(isOnline(manager))
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(isOnline(manager))
            }
        }

    manager.registerDefaultNetworkCallback(callback)
    launch {
        while (isActive) {
            delay(10_000)
            trySend(isOnline(manager))
        }
    }
    awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()

private fun currentOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    return manager != null && isOnline(manager)
}

private fun isOnline(manager: ConnectivityManager): Boolean {
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
