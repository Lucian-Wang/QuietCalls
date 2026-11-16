// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.ui.screen.settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import wang.lucian.quietcalls.viewmodel.SettingViewModel

@Composable
fun AboutPreferences(viewModel: SettingViewModel) {
    SettingsSection("静话") {
        Text("以本地规则为核心的个人防骚扰工具。支持境外、推销、陌生号码筛选及号码、地域通行管理，通讯录联系人始终优先放行。")
        SettingsGroup(rows = listOf(
            SettingsRowItem("version") { modifier -> SettingsItemRow(modifier, "应用版本", viewModel.versionName, icon = Icons.Default.Info, onClick = viewModel::onVersionClick) },
            SettingsRowItem("identity") { modifier -> SettingsItemRow(modifier, "静话 · 个人开源版", "在设备上管理筛选规则与通行名单，按需要选择离线或在线识别。", icon = Icons.Default.Shield) },
            SettingsRowItem("credits") { modifier -> SettingsItemRow(modifier, "开源来源", "基于 Pixel Telo 开发并修改。感谢 Pixel-Tailor-CN 及原作者；原项目采用 Apache License 2.0，本分支保留其许可证与版权归属。", icon = Icons.Default.Code) },
            SettingsRowItem("location-data") { modifier -> SettingsItemRow(modifier, "离线归属地数据", "phone.dat 来自 pangongzi/phone，遵循 MIT 许可。归属地不等于实时位置，亦不是骚扰名单。", icon = Icons.Default.LocationOn) },
        ))
    }
}
