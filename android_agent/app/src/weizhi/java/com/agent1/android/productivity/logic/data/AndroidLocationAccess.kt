package com.agent1.android.productivity.logic.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 前台单次定位（不引入 Play Services）。 */
class AndroidLocationAccess(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun getCurrent(coarseOnly: Boolean, timeoutMs: Long): JSONObject {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = preferredProviders(lm, coarseOnly)
        if (providers.isEmpty()) {
            return JSONObject()
                .put("error", "location_disabled")
                .put("message", "No location provider enabled on device")
        }
        for (provider in providers) {
            val cached = lm.getLastKnownLocation(provider)
            if (cached != null && isFreshEnough(cached)) {
                return locationJson(cached)
            }
        }
        val timeout = timeoutMs.coerceIn(1_000L, 60_000L)
        for (provider in providers) {
            val fresh = requestSingleFix(lm, provider, timeout)
            if (fresh != null) {
                return locationJson(fresh)
            }
        }
        return JSONObject()
            .put("error", "timeout")
            .put("message", "Could not obtain location within ${timeout}ms")
    }

    private fun preferredProviders(lm: LocationManager, coarseOnly: Boolean): List<String> {
        val out = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                out.add(LocationManager.FUSED_PROVIDER)
            }
        }
        if (!coarseOnly && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            out.add(LocationManager.GPS_PROVIDER)
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            out.add(LocationManager.NETWORK_PROVIDER)
        }
        return out.distinct()
    }

    private fun isFreshEnough(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        return ageMs in 0..15 * 60 * 1000L
    }

    @SuppressLint("MissingPermission")
    private fun requestSingleFix(lm: LocationManager, provider: String, timeoutMs: Long): Location? {
        val latch = CountDownLatch(1)
        var result: Location? = null
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                result = location
                latch.countDown()
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            }

            override fun onProviderEnabled(provider: String) {
            }

            override fun onProviderDisabled(provider: String) {
            }
        }
        lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return null
            }
        } catch (_: InterruptedException) {
            return null
        } finally {
            lm.removeUpdates(listener)
        }
        return result
    }

    private fun locationJson(location: Location): JSONObject {
        return JSONObject()
            .put("latitude", location.latitude)
            .put("longitude", location.longitude)
            .put("accuracyMeters", location.accuracy.toDouble())
            .put("provider", location.provider.orEmpty())
            .put("timestampMs", location.time)
    }
}
