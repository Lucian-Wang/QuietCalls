// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.ui.screen.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import wang.lucian.quietcalls.receiver.BlockedCallNotifier

/** 通知权限只在用户点击时申请；渠道已关闭时直接打开系统通知设置。 */
@Composable
fun BlockedNotificationPreference() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(android.app.NotificationManager::class.java)
                .getNotificationChannel(BlockedCallNotifier.CHANNEL_ID)?.importance != android.app.NotificationManager.IMPORTANCE_NONE
    }
    SettingsSection("拦截通知") {
        SettingsGroup(listOf(SettingsRowItem("blocked-notifications") { modifier ->
            SettingsItemRow(modifier, "静默拦截通知",
                if (granted) "已开启 · 不响铃、不振动，多条合并为一条；点击查看系统设置"
                else "通知未开启，点此授权；拒接功能仍可正常运行",
                icon = Icons.Default.NotificationsNone,
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                            Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        request.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        BlockedCallNotifier.ensureChannel(context)
                        context.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, BlockedCallNotifier.CHANNEL_ID))
                    }
                })
        }))
    }
}
