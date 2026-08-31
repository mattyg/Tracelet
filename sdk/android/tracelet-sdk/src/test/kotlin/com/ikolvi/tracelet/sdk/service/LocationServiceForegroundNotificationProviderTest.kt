package com.ikolvi.tracelet.sdk.service

import android.app.Notification
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.os.Bundle
import androidx.lifecycle.LifecycleOwner
import androidx.test.core.app.ApplicationProvider
import com.ikolvi.tracelet.sdk.ConfigManager
import com.ikolvi.tracelet.sdk.StateManager
import com.ikolvi.tracelet.sdk.notification.ForegroundNotificationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocationServiceForegroundNotificationProviderTest {
    private lateinit var context: Context
    private lateinit var serviceController: org.robolectric.android.controller.ServiceController<LocationService>

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ConfigManager.getInstance(context).setConfig(
            mapOf(
                "app" to mapOf(
                    "foregroundService" to mapOf(
                        "channelId" to "tracelet_provider_test",
                        "notificationTitle" to "Tracelet fallback",
                    ),
                ),
            ),
        )
        TestForegroundNotificationProvider.reset()
        val serviceComponent = ComponentName(context, LocationService::class.java)
        val packageManager = shadowOf(context.packageManager)
        val serviceInfo = packageManager.addServiceIfNotPresent(serviceComponent).apply {
            metaData = Bundle().apply {
                putString(
                    ForegroundNotificationProvider.METADATA_KEY,
                    TestForegroundNotificationProvider::class.java.name,
                )
            }
        }
        packageManager.addOrUpdateService(serviceInfo)
        serviceController = Robolectric.buildService(LocationService::class.java)
    }

    @After
    fun tearDown() {
        serviceController.destroy()
        StateManager(context).enabled = false
    }

    @Test
    fun `initial foreground promotion uses host provider notification`() {
        startService()

        assertEquals("Host notification", foregroundNotification().extras.getString(Notification.EXTRA_TITLE))
        assertEquals(1, TestForegroundNotificationProvider.invocationCount)
    }

    @Test
    fun `missing provider metadata uses standard notification`() {
        val serviceComponent = ComponentName(context, LocationService::class.java)
        val packageManager = shadowOf(context.packageManager)
        val serviceInfo = packageManager.addServiceIfNotPresent(serviceComponent).apply {
            metaData = null
        }
        packageManager.addOrUpdateService(serviceInfo)

        startService()

        assertEquals("Tracelet fallback", foregroundNotification().extras.getString(Notification.EXTRA_TITLE))
        assertEquals(0, TestForegroundNotificationProvider.invocationCount)
    }

    @Test
    fun `null provider result falls back to standard notification`() {
        TestForegroundNotificationProvider.useCustomNotification = false

        startService()

        assertEquals("Tracelet fallback", foregroundNotification().extras.getString(Notification.EXTRA_TITLE))
        assertEquals(1, TestForegroundNotificationProvider.invocationCount)
    }

    @Test
    fun `explicit notification update invokes provider again`() {
        startService()

        serviceController.withIntent(
            Intent().setAction(LocationService.ACTION_UPDATE_NOTIFICATION),
        ).startCommand(0, 2)

        assertEquals(2, TestForegroundNotificationProvider.invocationCount)
        assertEquals("Host notification", foregroundNotification().extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `background lifecycle rebuild invokes provider again`() {
        startService()

        serviceController.get().onStop(mock<LifecycleOwner>())

        assertEquals(2, TestForegroundNotificationProvider.invocationCount)
        assertEquals("Host notification", foregroundNotification().extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun `sticky process recovery discovers provider without application registration`() {
        StateManager(context).enabled = true
        serviceController.create().get().onStartCommand(null, 0, 1)

        assertEquals("Host notification", foregroundNotification().extras.getString(Notification.EXTRA_TITLE))
        assertEquals(1, TestForegroundNotificationProvider.invocationCount)
    }

    private fun startService() {
        serviceController.create()
            .withIntent(Intent().setAction(LocationService.ACTION_START))
            .startCommand(0, 1)
    }

    private fun foregroundNotification(): Notification =
        checkNotNull(shadowOf(serviceController.get()).lastForegroundNotification)
}

class TestForegroundNotificationProvider : ForegroundNotificationProvider {
    override fun createNotification(context: Context, fallback: Notification): Notification? {
        invocationCount += 1
        if (!useCustomNotification) return null
        return NotificationCompat.Builder(context, "tracelet_provider_test")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Host notification")
            .build()
    }

    companion object {
        var invocationCount = 0
        var useCustomNotification = true

        fun reset() {
            invocationCount = 0
            useCustomNotification = true
        }
    }
}
