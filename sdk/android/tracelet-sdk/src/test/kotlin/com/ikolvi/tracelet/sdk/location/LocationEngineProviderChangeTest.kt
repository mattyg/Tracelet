package com.ikolvi.tracelet.sdk.location

import android.app.Application
import android.content.Intent
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.ikolvi.tracelet.sdk.ConfigManager
import com.ikolvi.tracelet.sdk.ListenerEventSender
import com.ikolvi.tracelet.sdk.StateManager
import com.ikolvi.tracelet.sdk.TraceletListener
import com.ikolvi.tracelet.sdk.wrapper.TraceletActivityRecognitionClient
import com.ikolvi.tracelet.sdk.wrapper.TraceletEventExtractor
import com.ikolvi.tracelet.sdk.wrapper.TraceletGeofencingClient
import com.ikolvi.tracelet.sdk.wrapper.TraceletLocationClient
import com.ikolvi.tracelet.sdk.wrapper.TraceletServices
import com.ikolvi.tracelet.sdk.wrapper.TraceletServicesProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LocationEngineProviderChangeTest {
    private lateinit var context: Application
    private lateinit var engine: LocationEngine
    private val providerChanges = mutableListOf<Map<String, Any?>>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val locationManager = context.getSystemService(LocationManager::class.java)
        shadowOf(locationManager).setLocationEnabled(true)

        TraceletServices.setProvider(object : TraceletServicesProvider {
            override fun getLocationClient(context: android.content.Context) =
                mock<TraceletLocationClient>()

            override fun getGeofencingClient(context: android.content.Context) =
                mock<TraceletGeofencingClient>()

            override fun getActivityRecognitionClient(context: android.content.Context) =
                mock<TraceletActivityRecognitionClient>()

            override fun getEventExtractor() = mock<TraceletEventExtractor>()
        })

        val events = ListenerEventSender().apply {
            listener = object : TraceletListener {
                override fun onProviderChange(data: Map<String, Any?>) {
                    providerChanges += data
                }
            }
        }
        engine = LocationEngine(
            context,
            ConfigManager.getInstance(context),
            StateManager(context),
            events,
        )
    }

    @After
    fun tearDown() {
        engine.destroy()
        ConfigManager.resetInstance()
        val provider = TraceletServices::class.java.getDeclaredField("provider")
        provider.isAccessible = true
        provider.set(null, null)
    }

    @Test
    fun `provider changes emit while continuous tracking is stopped`() {
        val locationManager = context.getSystemService(LocationManager::class.java)
        shadowOf(locationManager).setLocationEnabled(false)

        context.sendBroadcast(Intent(LocationManager.MODE_CHANGED_ACTION))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(1, providerChanges.size)
        assertFalse(providerChanges.single()["enabled"] as Boolean)
    }

    @Test
    fun `duplicate provider broadcasts emit only once`() {
        val locationManager = context.getSystemService(LocationManager::class.java)
        shadowOf(locationManager).setLocationEnabled(false)
        val changed = Intent(LocationManager.MODE_CHANGED_ACTION)

        context.sendBroadcast(changed)
        context.sendBroadcast(changed)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(1, providerChanges.size)
    }

    @Test
    fun `destroy unregisters provider observer`() {
        engine.destroy()
        val locationManager = context.getSystemService(LocationManager::class.java)
        shadowOf(locationManager).setLocationEnabled(false)

        context.sendBroadcast(Intent(LocationManager.MODE_CHANGED_ACTION))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(emptyList(), providerChanges)
    }
}
