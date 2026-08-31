package com.ikolvi.tracelet.sdk.notification

import android.app.Notification
import android.content.Context

/**
 * Builds the foreground-service notification owned and published by Tracelet.
 *
 * Implementations are instantiated from [METADATA_KEY] when
 * `LocationService` is created. They must have a public zero-argument
 * constructor and must build synchronously from Android-owned state. A provider
 * must not depend on a Flutter engine, network request, or asynchronous setup.
 */
fun interface ForegroundNotificationProvider {
    /**
     * Returns a host notification, or `null` to publish [fallback].
     *
     * Tracelet invokes this for the initial foreground promotion and every
     * subsequent notification rebuild. Exceptions are treated as foreground
     * promotion failures rather than silently replacing the host presentation.
     */
    fun createNotification(context: Context, fallback: Notification): Notification?

    companion object {
        const val METADATA_KEY = "com.ikolvi.tracelet.FOREGROUND_NOTIFICATION_PROVIDER"
    }
}
