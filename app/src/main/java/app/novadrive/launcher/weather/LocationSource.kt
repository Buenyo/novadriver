package app.novadrive.launcher.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Localização só para o clima: usa a última posição conhecida e, se preciso, um único pedido curto. */
class LocationSource(private val context: Context) {
    private val lm = context.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun lastKnown(): Location? {
        if (!hasPermission()) return null
        return lm.getProviders(false)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    /** Última posição com até 3 h; senão pede uma leitura com limite de tempo (GPS pode demorar na garagem). */
    @SuppressLint("MissingPermission")
    suspend fun current(timeoutMs: Long = 45_000): Location? {
        if (!hasPermission()) return null
        lastKnown()?.takeIf { System.currentTimeMillis() - it.time < 3 * 3600_000L }?.let { return it }
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
            ?: return lastKnown()
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("API antiga, exigida no Android 7")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                try {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        } ?: lastKnown()
    }
}
