package com.example.assistente

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/** Serviço de acessibilidade: único jeito oficial do Android de tirar print por código. */
class ScreenshotService : AccessibilityService() {

    companion object {
        @Volatile var instance: ScreenshotService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    /** Vai para a tela inicial (para não fotografar o próprio app) e tira o print. */
    fun tirarPrint() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        Handler(Looper.getMainLooper()).postDelayed({
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
        }, 1200)
    }
}
