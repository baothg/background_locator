package com.thgbao.background_locator.pluggables

import android.content.Context
import com.thgbao.background_locator.IsolateHolderService
import com.thgbao.background_locator.Keys
import com.thgbao.background_locator.PreferencesManager

class DisposePluggable : Pluggable {
    override fun setCallback(context: Context, callbackHandle: Long) {
        PreferencesManager.setCallbackHandle(context, Keys.DISPOSE_CALLBACK_HANDLE_KEY, callbackHandle)
    }

    override fun onServiceDispose(context: Context) {
        (PreferencesManager.getCallbackHandle(context, Keys.DISPOSE_CALLBACK_HANDLE_KEY))?.let { disposeCallback ->
            IsolateHolderService.invokeBackgroundMethod(
                context,
                Keys.BCM_DISPOSE,
                hashMapOf(Keys.ARG_DISPOSE_CALLBACK to disposeCallback)
            )
        }
    }
}