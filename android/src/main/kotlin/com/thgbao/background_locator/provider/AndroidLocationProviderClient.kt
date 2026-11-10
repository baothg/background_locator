package com.thgbao.background_locator.provider

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
import android.os.Bundle

class AndroidLocationProviderClient(context: Context, override var listener: LocationUpdateListener?) : BLLocationProvider, LocationListener {
    private val client: LocationManager? =
            ContextCompat.getSystemService(context, LocationManager::class.java)

    private var bestAccuracy: Float? = null
    private var timeOfLastLocation: Long = 0L
    private var timeBetweenLocation: Long = 0L

    @SuppressLint("MissingPermission")
    override fun removeLocationUpdates() {
        client?.removeUpdates(this)
    }

    @SuppressLint("MissingPermission")
    override fun requestLocationUpdates(request: LocationRequestOptions) {
        val manager = client ?: throw IllegalStateException("LocationManager is unavailable")
        val providers = supportedAndroidProviders(manager.allProviders)
        check(providers.isNotEmpty()) { "No supported Android location providers" }
        timeBetweenLocation = request.interval
        providers.forEach { provider ->
            manager.requestLocationUpdates(provider, request.interval, request.distanceFilter, this)
        }
        // return the android device last Location after start request location
        providers.mapNotNull { manager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }?.let { onLocationChanged(it) }
    }

    override fun onLocationChanged(location: Location) {
        //whenever the expected time period is reached invalidate the last known accuracy
        // so that we don't just receive better and better accuracy and eventually risk receiving
        // only minimal locations
        val intervalElapsed = location.time - timeOfLastLocation >= timeBetweenLocation
        if (intervalElapsed) {
            bestAccuracy = null
        }
        //ensure that we don't get a lot of events
        // or if enabled, only get more accurate events within mTimeBetweenLocationEvents
        val accuracy = location.accuracy.takeIf {
            location.hasAccuracy() && it.isFinite() && it > 0.0f
        }
        val improvedAccuracy = accuracy != null &&
            (bestAccuracy == null || accuracy < bestAccuracy!!)
        if (intervalElapsed || improvedAccuracy) {
            //be sure to store the time of receiving this event !
            timeOfLastLocation = location.time
            bestAccuracy = accuracy
            //send message to parent containing the location object
            listener?.onLocationUpdated(LocationParserUtil.getLocationMapFromLocation(location))
        }
    }

    override fun onProviderDisabled(provider: String) {
        // nop
    }

    override fun onProviderEnabled(provider: String) {
        // nop
    }
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

}