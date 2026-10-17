package id.kasirq.dailysunnah.ibadah.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

data class PrayerCoordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val updatedAtMillis: Long,
)

object PrayerLocationStore {
    private const val PREFS = "prayer_location"

    fun read(context: Context): PrayerCoordinates? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("latitude") || !prefs.contains("longitude")) return null
        val latitude = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return null
        val longitude = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return PrayerCoordinates(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = prefs.getFloat("accuracy", 0f),
            updatedAtMillis = prefs.getLong("updated_at", 0L),
        )
    }

    fun save(context: Context, coordinates: PrayerCoordinates) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("latitude", coordinates.latitude.toString())
            .putString("longitude", coordinates.longitude.toString())
            .putFloat("accuracy", coordinates.accuracyMeters)
            .putLong("updated_at", coordinates.updatedAtMillis)
            .apply()
    }
}

/** Requests a fresh foreground fix and stores the most accurate result locally. */
object PrecisePrayerLocationProvider {
    fun refresh(
        context: Context,
        onLocation: (PrayerCoordinates) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onFailure("Izin lokasi presisi belum diberikan.")
            return
        }

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = manager.getProviders(true).filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }
        if (providers.isEmpty()) {
            onFailure("Aktifkan layanan lokasi perangkat untuk mendapatkan koordinat.")
            return
        }

        var bestLocation: Location? = null
        var finished = false
        val handler = Handler(Looper.getMainLooper())
        lateinit var listener: LocationListener

        fun stopUpdates() {
            if (finished) return
            finished = true
            handler.removeCallbacksAndMessages(null)
            runCatching { manager.removeUpdates(listener) }
        }

        fun publish(location: Location) {
            val coordinates = PrayerCoordinates(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracyMeters = location.accuracy,
                updatedAtMillis = location.time,
            )
            PrayerLocationStore.save(context, coordinates)
            onLocation(coordinates)
        }

        listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!location.hasAccuracy()) return
                val previous = bestLocation
                val previousIsOld = previous != null && System.currentTimeMillis() - previous.time > 15 * 60 * 1000L
                if (previous == null || location.accuracy <= previous.accuracy || previousIsOld) {
                    bestLocation = location
                    publish(location)
                }
                if (location.accuracy <= 50f) stopUpdates()
            }

            @Deprecated("Deprecated by Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        providers.forEach { provider ->
            runCatching {
                manager.getLastKnownLocation(provider)?.takeIf {
                    System.currentTimeMillis() - it.time <= 6 * 60 * 60 * 1000L
                }?.let { location ->
                    val previous = bestLocation
                    if (previous == null || location.accuracy < previous.accuracy) {
                        bestLocation = location
                        publish(location)
                    }
                }
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }.onFailure { error ->
                if (bestLocation == null) onFailure(error.localizedMessage ?: "Lokasi perangkat tidak dapat dibaca.")
            }
        }

        handler.postDelayed({
            if (!finished) {
                stopUpdates()
                if (bestLocation == null) onFailure("Belum mendapat koordinat presisi. Coba lagi di area terbuka.")
            }
        }, 12_000L)
    }
}
