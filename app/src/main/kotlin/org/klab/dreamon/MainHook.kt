package org.klab.dreamon

import android.app.Application
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class MainHook : XposedModule() {

    companion object {
        private const val TAG = "DreamOn"

        private val TARGET_PACKAGES = setOf(
            "com.google.assistant.hubui",
            "com.google.android.apps.weather"
        )

        private val DREAM_KEYS = setOf(
            "com.google.android.apps.dreamliner.45711569", // Everyday Clock
            "com.google.android.apps.dreamliner.45711570", // Pilot Bold
            "com.google.android.apps.dreamliner.45711571", // Photos
            "com.google.android.apps.dreamliner.45711572"  // Weather
        )
    }

    override fun onPackageReady(param: PackageReadyParam) {
        super.onPackageReady(param)
        if (!TARGET_PACKAGES.contains(param.packageName)) return

        Log.d(TAG, "Loading module for ${param.packageName} (Ready)")

        try {
            val getIntMethod = Settings.Secure::class.java.getDeclaredMethod(
                "getInt",
                ContentResolver::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            hook(getIntMethod).intercept { chain ->
                val key = chain.args[1] as String
                if (DREAM_KEYS.contains(key)) {
                } else {
                    chain.proceed()
                }
            }

            val classLoader = param.classLoader
            val apmClass = classLoader.loadClass("android.app.ApplicationPackageManager")
            val setComponentEnabledSettingMethod = apmClass.getDeclaredMethod(
                "setComponentEnabledSetting",
                ComponentName::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            hook(setComponentEnabledSettingMethod).intercept { chain ->
                val componentName = chain.args[0] as ComponentName
                val newState = chain.args[1] as Int

                val className = componentName.className
                if (className.contains("EverydayClockDreamService") ||
                    className.contains("PilotBoldClockDreamService") ||
                    className.contains("PersonalPhotoDreamService") ||
                    className.contains("WeatherDreamService")) {

                    if (newState == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        chain.args[1] = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        Log.d(TAG, "Prevented disabling of ${componentName.shortClassName}")
                    }
                }
                chain.proceed()
            }

            val onCreateMethod = Application::class.java.getDeclaredMethod("onCreate")
            hook(onCreateMethod).intercept { chain ->
                val result = chain.proceed()
                val context = chain.thisObject as Context
                val pkgName = context.packageName

                try {
                    val pm = context.packageManager
                    val packageInfo = pm.getPackageInfo(pkgName, PackageManager.GET_SERVICES)

                    packageInfo.services?.forEach { serviceInfo ->
                        if (serviceInfo.permission == "android.permission.BIND_DREAM_SERVICE") {
                            val component = ComponentName(pkgName, serviceInfo.name)

                            if (pm.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                                pm.setComponentEnabledSetting(
                                    component,
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                    PackageManager.DONT_KILL_APP
                                )
                                Log.d(TAG, "Service ${serviceInfo.name} force enabled in $pkgName")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during scan in $pkgName: ${e.message}")
                }
                result
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during hooking ${param.packageName}: ${e.message}")
        }
    }
}