package com.thgbao.background_locator.provider

import java.util.HashMap

interface LocationUpdateListener {
    fun onLocationUpdated(location: HashMap<Any, Any>?)
    fun onLocationError(error: Exception) {}
}