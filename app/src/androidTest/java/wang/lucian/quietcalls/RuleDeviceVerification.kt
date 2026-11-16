// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls

import android.app.Instrumentation
import android.os.Bundle
import android.content.ContentValues
import android.content.ContentUris
import android.provider.ContactsContract
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import wang.lucian.quietcalls.data.MastDatabase
import wang.lucian.quietcalls.data.entity.ListType
import wang.lucian.quietcalls.data.repository.SpamNumberRepository
import wang.lucian.quietcalls.data.repository.UserListRepository
import wang.lucian.quietcalls.data.rules.*

/** 仅在测试设备验证真实 Repository、联系人与离线库，不拨打电话、不访问在线服务。 */
class RuleDeviceVerification : Instrumentation() {
    private var direct360Only = false
    override fun onCreate(arguments: Bundle?) {
        direct360Only = arguments?.getString("mode") == "direct360"
        super.onCreate(arguments)
        start()
    }
    override fun onStart() {
        waitForIdleSync()
        if (direct360Only) {
            verifyDirect360(this)
            return
        }
        val output = Bundle()
        var rawId: Long? = null
        var database: MastDatabase? = null
        val prefs = targetContext.getSharedPreferences("pixel_telo", 0)
        val original = prefs.all.toMap()
        var fixtureCreated = false
        try { runBlocking {
            check(!targetContext.getDatabasePath("mast.db").exists()) { "Use a fresh test installation" }
            prefs.edit().putBoolean("no_network_query", true).putBoolean("notify_only", false).commit()
            val repository = GlobalContext.get().get<SpamNumberRepository>()
            check(CallRuleMatcher.internalNumber("650"))
            check(CallRuleMatcher.internalNumber("6123"))
            check(CallRuleMatcher.internalNumber("61234"))
            check(!CallRuleMatcher.internalNumber("612345"))
            check(!CallRuleMatcher.internalNumber("+86650"))
            check(!CallRuleMatcher.internalNumber("60000000000"))
            check(CallRuleMatcher.foreignKind("+12025550123") == 1)
            check(CallRuleMatcher.foreignKind("0085212345678") == 2)
            check(CallRuleMatcher.foreignKind("+8613800138000") == 0)
            val special = CallRuleSettings(blockVirtualOperator = true, block400 = true, block95 = true, block96 = true)
            listOf("16200000000", "+8616500000000", "008617000000000", "4001234567", "95588", "950123456", "96123").forEach {
                check(CallRuleMatcher.specialBlock(it, special) != null) { "Special prefix not matched" }
            }
            listOf("13800138000", "+19558812345", "170", "40012", "951").forEach {
                check(CallRuleMatcher.specialBlock(it, special) == null) { "Special prefix false positive" }
            }
            check(CallRuleMatcher.specialBlock("95588", CallRuleSettings()) == null)
            check(OfflinePhoneLocation.regions(targetContext).isNotEmpty())
            special.save(prefs)
            check(repository.checkSpam("95588").shouldBlock)
            special.copy(allowedNumbers = "95588").save(prefs)
            check(!repository.checkSpam("95588").shouldBlock)
            val lists = GlobalContext.get().get<UserListRepository>()
            check(!lists.add("快递", false, ListType.WHITE, null, tagMatch = true))
            check(lists.findWhiteListTagMatch("快递") == null)
            check(!lists.add("营销", false, ListType.BLACK, null, tagMatch = true))
            check(lists.findBlackListTagMatch("营销") == null)
            val regionLocation = OfflinePhoneLocation.lookup(targetContext, "13800138001") ?: error("Missing region")
            val regionName = "${regionLocation.province}/${regionLocation.city}"
            val savedBlackRegions = lists.getAllByType(ListType.BLACK).filter { it.locationMatch && !it.tagMatch }.map { it.phoneNumber }
            val savedWhiteRegions = lists.getAllByType(ListType.WHITE).filter { it.locationMatch && !it.tagMatch }.map { it.phoneNumber }
            val listDao = GlobalContext.get().get<wang.lucian.quietcalls.data.dao.UserListDao>()
            try {
                CallRuleSettings().save(prefs)
                lists.replaceLocationRules(ListType.BLACK, listOf(regionName))
                check(lists.findBlackListLocationMatch(regionLocation) != null)
                check(repository.checkSpam("13800138001").shouldBlock) { "Offline selected city must block" }
                lists.replaceLocationRules(ListType.BLACK, emptyList())
                check(!lists.add(regionName, false, ListType.WHITE, null, locationMatch = true))
                listDao.replaceLocationRules(ListType.WHITE, listOf(regionName))
                CallRuleSettings(blockStrangers = true).save(prefs)
                lists.migrateLegacyWhiteRegions(prefs)
                lists.migrateLegacyWhiteRegions(prefs)
                check(lists.findWhiteListLocationMatch(regionLocation) == null)
                check(CallRuleMatcher.entries(CallRuleSettings.read(prefs).allowedRegions).count { it == regionName } == 1)
                check(!repository.checkSpam("13800138001").shouldBlock) { "Migrated region must remain stranger exception" }
                check(lists.add("4001234567", false, ListType.WHITE, null))
                try {
                    CallRuleSettings(block400 = true, blockStrangers = true).save(prefs)
                    check(!repository.checkSpam("4001234567").shouldBlock) { "Number whitelist must override all rules" }
                } finally { lists.findWhiteListMatch("4001234567")?.let { lists.delete(it) } }
            } finally {
                lists.replaceLocationRules(ListType.BLACK, savedBlackRegions)
                listDao.replaceLocationRules(ListType.WHITE, savedWhiteRegions)
            }
            var settings = CallRuleSettings(blockInternational = true, blockStrangers = true)
            settings.save(prefs)
            check(!repository.checkSpam("650").shouldBlock)
            check(repository.checkSpam("+12025550123").label.contains("境外"))
            settings = settings.copy(blockHongKongMacaoTaiwan = true)
            settings.save(prefs)
            check(repository.checkSpam("+85212345678").shouldBlock)
            val location = OfflinePhoneLocation.lookup(targetContext, "13800138000") ?: error("Missing location")
            settings = settings.copy(allowedRegions = "${location.province}/${location.city}")
            settings.save(prefs)
            check(!repository.checkSpam("13800138001").shouldBlock) { "Allowed city normal call" }
            database = Room.databaseBuilder(targetContext, MastDatabase::class.java, "mast.db").build()
            database!!.openHelper.writableDatabase.execSQL("INSERT INTO spam_numbers(phone_number,tag,source) VALUES(?,?,?)", arrayOf("13800138000", "骚扰电话", "device_fixture"))
            fixtureCreated = true
            check(repository.checkSpam("13800138000").shouldBlock) { "Region must not bypass spam" }
            check(lists.add("13800138000", false, ListType.WHITE, null))
            try { check(!repository.checkSpam("13800138000").shouldBlock) { "Whitelist must override known spam" } }
            finally { lists.findWhiteListMatch("13800138000")?.let { lists.delete(it) } }
            settings.copy(allowedNumbers = "13800138000").save(prefs)
            check(!repository.checkSpam("13800138000").shouldBlock) { "Exact allow list" }
            settings.copy(allowedRegions = "").save(prefs)
            check(repository.checkSpam("13800138001").shouldBlock) { "Stranger rule must work offline" }
            val resolver = targetContext.contentResolver
            val raw = resolver.insert(ContactsContract.RawContacts.CONTENT_URI, ContentValues()) ?: error("Contact insert")
            rawId = ContentUris.parseId(raw)
            resolver.insert(ContactsContract.Data.CONTENT_URI, ContentValues().apply {
                put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, "13800138000")
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
            })
            check(!repository.checkSpam("13800138000").shouldBlock) { "Contacts must override spam" }
            val notifications = targetContext.getSystemService(android.app.NotificationManager::class.java)
            val notifier = wang.lucian.quietcalls.receiver.BlockedCallNotifier
            notifier.acknowledge(targetContext)
            notifier.onBlocked(targetContext, "4001234567", "特殊号段")
            notifier.onBlocked(targetContext, "95588", "号码黑名单")
            repeat(30) { if (notifications.activeNotifications.none { it.id == notifier.NOTIFICATION_ID && it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString().contains("2") }) kotlinx.coroutines.delay(50) }
            val active = notifications.activeNotifications.filter { it.id == notifier.NOTIFICATION_ID }
            check(active.size == 1) { "Blocked calls must merge into one notification" }
            val notification = active.single().notification
            check(notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString().contains("2"))
            check(notification.extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)?.size == 2)
            val channel = notifications.getNotificationChannel(notifier.CHANNEL_ID)
            check(channel.importance == android.app.NotificationManager.IMPORTANCE_LOW)
            check(channel.sound == null && !channel.shouldVibrate()) { "Blocked channel must be silent" }
            notification.deleteIntent.send()
            repeat(30) { if (notifications.activeNotifications.any { it.id == notifier.NOTIFICATION_ID }) kotlinx.coroutines.delay(50) }
            check(notifications.activeNotifications.none { it.id == notifier.NOTIFICATION_ID }) { "Dismiss must clear notification" }
            notifier.onBlocked(targetContext, "95588", "号码黑名单")
            val fresh = notifications.activeNotifications.single { it.id == notifier.NOTIFICATION_ID }.notification
            check(fresh.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString().contains("1"))
            fresh.contentIntent.send()
            repeat(40) { if (notifications.activeNotifications.any { it.id == notifier.NOTIFICATION_ID }) kotlinx.coroutines.delay(50) }
            check(notifications.activeNotifications.none { it.id == notifier.NOTIFICATION_ID }) { "Open app must acknowledge notifications" }
            notifier.acknowledge(targetContext)
            output.putString("notifications", "PASS: merged count, inbox lines, low importance, no sound/vibration, dismiss reset and tap acknowledge")
            output.putString("result", "PASS: internal 3-5 digits, international/HMT, offline location, stranger blocking, region does not bypass spam, exact allow list and contact priority")
        } } catch (error: Throwable) { output.putString("error", error.toString()) }
        finally {
            rawId?.let { runCatching { targetContext.contentResolver.delete(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, it), null, null) } }
            database?.close()
            if (fixtureCreated) targetContext.deleteDatabase("mast.db")
            val editor = prefs.edit().clear()
            original.forEach { (key,value) -> when(value) {
                is String -> editor.putString(key,value)
                is Boolean -> editor.putBoolean(key,value)
                is Int -> editor.putInt(key,value)
                is Long -> editor.putLong(key,value)
                is Float -> editor.putFloat(key,value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key,value as Set<String>)
            } }
            editor.commit()
        }
        finish(if (output.containsKey("error")) 1 else 0, output)
    }
}
