// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls

import kotlinx.coroutines.*
import wang.lucian.quietcalls.data.rules.OfflinePhoneLocation
import android.app.Application
import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import wang.lucian.quietcalls.di.appModule
import wang.lucian.quietcalls.receiver.CallStateVibrationController
import wang.lucian.quietcalls.worker.OfflineDatabaseUpdateScheduler

class PixelTeloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        wang.lucian.quietcalls.receiver.BlockedCallNotifier.ensureChannel(this)
        startKoin {
            androidContext(this@PixelTeloApplication)
            modules(appModule)
        }
        val prefs = getSharedPreferences("pixel_telo", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("quiet_calls_initialized", false)) {
            prefs.edit().putBoolean("no_network_query", true).putBoolean("notify_only", false)
                .putBoolean("quiet_calls_initialized", true).apply()
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { OfflinePhoneLocation.prepare(this@PixelTeloApplication) }
        }
        CallStateVibrationController.normalizePreference(this, prefs)
        OfflineDatabaseUpdateScheduler.ensureScheduled(this, prefs)
    }
}
