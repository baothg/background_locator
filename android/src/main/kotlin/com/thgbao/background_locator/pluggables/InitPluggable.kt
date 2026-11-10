package com.thgbao.background_locator.pluggables

import android.content.Context
import com.thgbao.background_locator.IsolateHolderService
import com.thgbao.background_locator.Keys
import com.thgbao.background_locator.PreferencesManager

class InitPluggable : Pluggable {
    private var isInitCallbackCalled = false

    override fun setCallback(context: Context, callbackHandle: Long) {
        PreferencesManager.setCallbackHandle(context, Keys.INIT_CALLBACK_HANDLE_KEY, callbackHandle)

    }

    override fun onServiceStart(context: Context) {
        if (!isInitCallbackCalled) {
            (PreferencesManager.getCallbackHandle(context, Keys.INIT_CALLBACK_HANDLE_KEY))?.let { initCallback ->
                val wasKilled = PreferencesManager.consumeWasKilled(context)
                val initialDataMap =
                    PreferencesManager.getDataCallback(context, Keys.INIT_DATA_CALLBACK_KEY) +
                        if (wasKilled) mapOf(Keys.ARG_WAS_KILLED to true) else emptyMap()
                IsolateHolderService.invokeBackgroundMethod(
                    context,
                    Keys.BCM_INIT,
                    hashMapOf(
                        Keys.ARG_INIT_CALLBACK to initCallback,
                        Keys.ARG_INIT_DATA_CALLBACK to initialDataMap
                    )
                )
            }
            isInitCallbackCalled = true
        }
    }

    override fun onServiceDispose(context: Context) {
        isInitCallbackCalled = false
    }

    fun setInitData(context: Context, data: Map<*, *>) {
        PreferencesManager.setDataCallback(context, Keys.INIT_DATA_CALLBACK_KEY, data)
    }
}