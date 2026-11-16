// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.ui.screen.settings

import wang.lucian.quietcalls.R

/** 设置首页提供的七个分类入口。 */
enum class SettingsCategory(val titleResId: Int) {
    RULES(R.string.quiet_category_rules),
    SPECIAL_NUMBERS(R.string.quiet_category_special),
    ALLOW_LISTS(R.string.quiet_category_allow),
    CARRIER_PROTECTION(R.string.quiet_category_carrier),
    CALL_RECOGNITION(R.string.category_call_recognition),
    ONLINE_QUERY(R.string.category_online_query),
    OFFLINE_DATABASE(R.string.category_offline_database),
    DISPLAY_ALERTS(R.string.category_display_alerts),
    RECORDS_BACKUP(R.string.category_records_backup),
    PERMISSIONS(R.string.category_permissions),
    ABOUT(R.string.category_about),
}
