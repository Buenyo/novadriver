package app.novadrive.launcher.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/** Hora atual, emitida a cada minuto e quando a hora/fuso mudam (sem timer próprio). */
fun clockFlow(context: Context): Flow<Long> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            trySend(System.currentTimeMillis())
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_TIME_TICK)
        addAction(Intent.ACTION_TIME_CHANGED)
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
    }
    context.registerReceiver(receiver, filter)
    trySend(System.currentTimeMillis())
    awaitClose { context.unregisterReceiver(receiver) }
}.conflate()

/** true quando há rede padrão disponível (a central costuma usar o hotspot do celular). */
fun networkAvailableFlow(context: Context): Flow<Boolean> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(true)
        }

        override fun onLost(network: Network) {
            trySend(false)
        }
    }
    @Suppress("DEPRECATION")
    trySend(cm.activeNetworkInfo?.isConnected == true)
    // registerDefaultNetworkCallback existe desde a API 24.
    cm.registerDefaultNetworkCallback(callback)
    awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
}.distinctUntilChanged()
