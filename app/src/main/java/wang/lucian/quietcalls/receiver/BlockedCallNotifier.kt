// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import wang.lucian.quietcalls.MainActivity
import wang.lucian.quietcalls.R

/** 单一静默通知，独立保存未读聚合；不将电话号码写入日志或系统备份。 */
object BlockedCallNotifier {
    const val CHANNEL_ID = "quiet_blocked_calls"
    const val NOTIFICATION_ID = 4106
    private const val PREFS = "quiet_blocked_notifications"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "静默拦截记录", NotificationManager.IMPORTANCE_LOW).apply {
            description = "拦截后合并显示一条通知，不响铃、不振动"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
        })
    }

    @Synchronized
    fun onBlocked(context: Context, number: String, reason: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = (prefs.getInt("count", 0).coerceAtLeast(0).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val old = runCatching { JSONArray(prefs.getString("lines", "[]")) }.getOrDefault(JSONArray())
        val line = number.replace('\n', ' ').take(40) + " · " + reason.ifBlank { "命中拦截规则" }.replace('\n', ' ').take(100)
        val lines = (0 until old.length()).map { old.optString(it) }.takeLast(4) + line
        prefs.edit().putInt("count", count).putString("lines", JSONArray(lines).toString()).apply()
        val open = PendingIntent.getActivity(context, 4106,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismiss = PendingIntent.getBroadcast(context, 4106, Intent(context, BlockedNotificationDismissReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val title = "已拦截 $count 个来电"
        val style = NotificationCompat.InboxStyle().setBigContentTitle(title).setSummaryText("点击查看来电记录")
        lines.forEach(style::addLine)
        val public = NotificationCompat.Builder(context, CHANNEL_ID).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText("打开静话查看详情").setSilent(true).build()
        manager.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(title).setContentText(line)
            .setStyle(style).setNumber(count).setContentIntent(open).setDeleteIntent(dismiss)
            .setAutoCancel(true).setOnlyAlertOnce(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public).build())
    }

    @Synchronized
    fun acknowledge(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

/** 用户划掉合并通知后，下次拦截从新的未读记录开始统计。 */
class BlockedNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { BlockedCallNotifier.acknowledge(context) }
}
