// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.ui.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import wang.lucian.quietcalls.data.rules.CallRuleMatcher
import wang.lucian.quietcalls.viewmodel.SettingViewModel

/** 设置按用户任务组织；同一规则只在对应详情页出现。 */
@Composable
fun QuietSettingsDashboard(
    viewModel: SettingViewModel,
    offlineSummary: String,
    onlineSummary: String,
    permissionsSummary: String,
    navigate: (SettingsCategory) -> Unit,
) {
    val rules = viewModel.callRules
    val generalCount = listOf(rules.blockInternational, rules.blockHongKongMacaoTaiwan,
        rules.blockMarketing, rules.blockStrangers).count { it }
    val specialCount = listOf(rules.blockVirtualOperator, rules.block400, rules.block95, rules.block96).count { it }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (viewModel.notifyOnly) "仅提示模式" else "按规则拦截", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("已开启 ${generalCount + specialCount} 项筛选 · 通讯录与号码白名单直接放行",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        SettingsSection("来电防护") {
            SettingsGroup(listOf(
                SettingsRowItem("rules") { m -> SettingsItemRow(m, "常规拦截", "境外、港澳台、推销、陌生号码 · 已开启 $generalCount 项", icon = Icons.Default.Security, onClick = { navigate(SettingsCategory.RULES) }) },
                SettingsRowItem("special") { m -> SettingsItemRow(m, "特殊号段", "虚拟运营商、400、95、96 · 已开启 $specialCount 项", icon = Icons.Default.PhoneDisabled, onClick = { navigate(SettingsCategory.SPECIAL_NUMBERS) }) },
                SettingsRowItem("allow") { m -> SettingsItemRow(m, "陌生号码通行", "${CallRuleMatcher.entries(rules.allowedNumbers).size} 个号码 · ${CallRuleMatcher.entries(rules.allowedRegions).size} 个地域 · 集团短号", icon = Icons.Default.VerifiedUser, onClick = { navigate(SettingsCategory.ALLOW_LISTS) }) },
                SettingsRowItem("carrier") { m -> SettingsItemRow(m, "运营商协同防护", "网络侧防骚扰 + 静话本机规则", icon = Icons.Default.Shield, onClick = { navigate(SettingsCategory.CARRIER_PROTECTION) }) },
                SettingsRowItem("behavior") { m -> SettingsItemRow(m, "拦截行为", "仅提示模式、重复来电与拦截策略", icon = Icons.Default.Tune, onClick = { navigate(SettingsCategory.CALL_RECOGNITION) }) },
            ))
        }
        SettingsSection("识别与数据") {
            SettingsGroup(listOf(
                SettingsRowItem("offline") { m -> SettingsItemRow(m, "离线骚扰号码库", offlineSummary, icon = Icons.Default.Storage, onClick = { navigate(SettingsCategory.OFFLINE_DATABASE) }) },
                SettingsRowItem("online") { m -> SettingsItemRow(m, "在线识别", onlineSummary, icon = Icons.Default.Cloud, onClick = { navigate(SettingsCategory.ONLINE_QUERY) }) },
            ))
        }
        SettingsSection("应用设置") {
            SettingsGroup(listOf(
                SettingsRowItem("permissions") { m -> SettingsItemRow(m, "权限与系统接管", permissionsSummary, icon = Icons.Default.AdminPanelSettings, onClick = { navigate(SettingsCategory.PERMISSIONS) }) },
                SettingsRowItem("display") { m -> SettingsItemRow(m, "来电显示与提醒", "原生来电信息、悬浮提示与振动", icon = Icons.Default.NotificationsNone, onClick = { navigate(SettingsCategory.DISPLAY_ALERTS) }) },
                SettingsRowItem("backup") { m -> SettingsItemRow(m, "备份与恢复", "导出或恢复来电记录和黑白名单", icon = Icons.Default.Backup, onClick = { navigate(SettingsCategory.RECORDS_BACKUP) }) },
                SettingsRowItem("about") { m -> SettingsItemRow(m, "关于静话", "${viewModel.versionName} · 开源来源与数据说明", icon = Icons.Default.Info, onClick = { navigate(SettingsCategory.ABOUT) }) },
            ))
        }
        Text("地域通行仅豁免陌生人拦截，命中骚扰库或已开启的特殊号段仍会拦截。",
            Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
}
