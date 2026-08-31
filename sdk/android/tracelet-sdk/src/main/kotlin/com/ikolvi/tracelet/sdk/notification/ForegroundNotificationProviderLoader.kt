package com.ikolvi.tracelet.sdk.notification

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.ikolvi.tracelet.sdk.service.LocationService

internal object ForegroundNotificationProviderLoader {
    fun load(context: Context): ForegroundNotificationProvider? {
        val serviceInfo = try {
            context.packageManager.getServiceInfo(
                ComponentName(context, LocationService::class.java),
                PackageManager.GET_META_DATA,
            )
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val providerClassName = serviceInfo.metaData
            ?.getString(ForegroundNotificationProvider.METADATA_KEY)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val providerClass = Class.forName(providerClassName, true, context.classLoader)
        return providerClass
            .asSubclass(ForegroundNotificationProvider::class.java)
            .getDeclaredConstructor()
            .newInstance()
    }
}
