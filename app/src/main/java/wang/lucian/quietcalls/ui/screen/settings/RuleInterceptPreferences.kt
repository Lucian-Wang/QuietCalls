// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.ui.screen.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import wang.lucian.quietcalls.data.PhoneNumberRuleMatcher
import wang.lucian.quietcalls.data.rules.CallRuleMatcher
import wang.lucian.quietcalls.viewmodel.SettingViewModel

/** 规则入口只分发配置，匹配与名单优先级由 Repository 统一处理。 */
@Composable
fun RuleInterceptPreferences(viewModel: SettingViewModel, category: SettingsCategory) {
    val rules = viewModel.callRules
    val context = LocalContext.current
    val hasContacts = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    var editor by remember { mutableStateOf<String?>(null) }
    fun edit(which: String, value: String) { editor = which }
    SettingsSection(title = when (category) {
        SettingsCategory.SPECIAL_NUMBERS -> "按号码类型拦截"
        SettingsCategory.ALLOW_LISTS -> "设置陌生号码通行例外"
        else -> "按来电身份拦截"
    }) {
        wang.lucian.quietcalls.ui.components.ExpandableHint(
            if (hasContacts) "规则与数据说明" else "需要通讯录权限",
            if (hasContacts) "通讯录与号码白名单直接放行。地域通行仅豁免陌生人筛选，仍检查骚扰与特殊号段。归属地不是实时位置；离线归属地数据为 phone.dat（2025-02），新号段、携号转网可能影响结果。"
            else "请授予通讯录权限。无法确认联系人时保守放行，避免误拦。",
        )
        if (viewModel.notifyOnly) Text("当前为仅提示模式：自动规则命中也不会挂断。可在下方关闭。", color = MaterialTheme.colorScheme.error)
        SettingsGroup(rows = listOf(
            SettingsRowItem("rule-overseas") { modifier -> SettingsSwitchRow(modifier,
                "拦截境外电话", "识别 + 或 00 国家代码；港澳台使用独立开关。", rules.blockInternational,
                Icons.Default.Public, onCheckedChange = { viewModel.updateCallRules(rules.copy(blockInternational = it)) }) },
            SettingsRowItem("rule-hmt") { modifier -> SettingsSwitchRow(modifier,
                "拦截港澳台电话", "明确以 +852、+853、+886 或对应 00 国家代码呼入。", rules.blockHongKongMacaoTaiwan,
                Icons.Default.PhoneDisabled, onCheckedChange = { viewModel.updateCallRules(rules.copy(blockHongKongMacaoTaiwan = it)) }) },
            SettingsRowItem("rule-marketing") { modifier -> SettingsSwitchRow(modifier,
                "拦截推销电话", "按号码库或在线标签中的营销、推销、广告等词匹配；无法识别不等于正常号码。", rules.blockMarketing,
                Icons.Default.Campaign, onCheckedChange = { viewModel.updateCallRules(rules.copy(blockMarketing = it)) }) },
            SettingsRowItem("rule-strangers") { modifier -> SettingsSwitchRow(modifier,
                "拦截陌生人电话", "不在通讯录且未命中号码或地域通行规则时拦截。归属地未知不豁免。", rules.blockStrangers,
                Icons.Default.PersonOff, enabled = hasContacts,
                onCheckedChange = { viewModel.updateCallRules(rules.copy(blockStrangers = it)) }) },
            SettingsRowItem("rule-virtual") { modifier -> SettingsSwitchRow(modifier,
                "拦截虚拟运营商号段", "162、165、167、170、171 开头的 11 位手机号。", rules.blockVirtualOperator,
                Icons.Default.PhoneDisabled, onCheckedChange = { viewModel.updateCallRules(rules.copy(blockVirtualOperator = it)) }) },
            SettingsRowItem("rule-400") { modifier -> SettingsSwitchRow(modifier,
                "拦截 400 号码", "400 开头的 10 位号码；也可能包含正常企业服务电话。", rules.block400,
                Icons.Default.Business, onCheckedChange = { viewModel.updateCallRules(rules.copy(block400 = it)) }) },
            SettingsRowItem("rule-95") { modifier -> SettingsSwitchRow(modifier,
                "拦截 95 号段", "95 开头、5 位及以上号码，包含 950、951 等；可能包含银行、快递客服。", rules.block95,
                Icons.Default.PhoneDisabled, onCheckedChange = { viewModel.updateCallRules(rules.copy(block95 = it)) }) },
            SettingsRowItem("rule-96") { modifier -> SettingsSwitchRow(modifier,
                "拦截 96 号段", "96 开头、5 位及以上号码；可能包含正常地方服务电话。", rules.block96,
                Icons.Default.PhoneDisabled, onCheckedChange = { viewModel.updateCallRules(rules.copy(block96 = it)) }) },
            SettingsRowItem("rule-short") { modifier -> SettingsSwitchRow(modifier,
                "允许集团内部短号", "直接放行 6 开头、总长度 3–5 位的纯数字短号，例如 650、6123、61234；六位以上不放行。", rules.allowInternal,
                Icons.Default.Business, onCheckedChange = { viewModel.updateCallRules(rules.copy(allowInternal = it)) }) },
            SettingsRowItem("rule-number-allow") { modifier -> SettingsItemRow(modifier,
                "陌生号码通行名单", "已添加 ${CallRuleMatcher.entries(rules.allowedNumbers).size} 个号码 · 精确匹配，优先于自动拦截", icon = Icons.Default.VerifiedUser,
                onClick = { edit("numbers", rules.allowedNumbers) }) },
            SettingsRowItem("rule-region-allow") { modifier -> SettingsItemRow(modifier,
                "陌生号码地域通行", "${rules.allowedRegions.ifBlank { "尚未设置" }} · 仅豁免陌生人拦截，仍检查骚扰库", icon = Icons.Default.LocationOn,
                onClick = { edit("regions", rules.allowedRegions) }) },
        ).filter { row ->
            when (category) {
                SettingsCategory.SPECIAL_NUMBERS -> row.key in setOf("rule-virtual", "rule-400", "rule-95", "rule-96")
                SettingsCategory.ALLOW_LISTS -> row.key in setOf("rule-short", "rule-number-allow", "rule-region-allow")
                else -> row.key in setOf("rule-overseas", "rule-hmt", "rule-marketing", "rule-strangers")
            }
        })
    }
    if (editor != null) AllowListManager(viewModel, editor == "regions") { editor = null }
}
