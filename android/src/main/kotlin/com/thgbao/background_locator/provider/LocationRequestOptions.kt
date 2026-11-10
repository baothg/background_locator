package com.thgbao.background_locator.provider

class LocationRequestOptions(val interval: Long, val accuracy: Int, val distanceFilter: Float)

internal fun supportedAndroidProviders(available: List<String>): List<String> =
    listOf("gps", "network").filter { it in available }