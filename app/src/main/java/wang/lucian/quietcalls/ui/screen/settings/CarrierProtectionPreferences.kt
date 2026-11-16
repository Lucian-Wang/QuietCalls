// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.ui.screen.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import wang.lucian.quietcalls.ui.components.ExpandableHint

/** 信息引导不接入运营商账户，不推断开通状态；外部操作由用户自行确认。 */
private data class CarrierGuide(val carrier: String, val service: String, val entry: String,
    val steps: String, val customerService: String, val officialUrl: String)

private val carrierGuides = listOf(
    CarrierGuide("中国移动", "高频骚扰电话防护", "中国移动高频骚扰电话防护",
        "微信搜索并核对公众号“中国移动高频骚扰电话防护”，查看“电话防护”中的业务开关和骚扰拦截设置；也可通过中国移动 App 或 10086 咨询办理。部分官方材料称为“来电来信免打扰”。",
        "10086", "https://gxca.miit.gov.cn/xwdt/xydt/art/2023/art_2a33aa2669474a6ea2a1ccdf7b886d35.html"),
    CarrierGuide("中国电信", "天翼防骚扰", "天翼防骚扰",
        "微信搜索并核对公众号“天翼防骚扰”，查看业务设置或智能拦截设置；也可访问官方业务网站或向 10000 咨询。“天翼防骚扰+”等增强服务可能收费。",
        "10000", "https://donotcall.189.cn/"),
    CarrierGuide("中国联通", "联通安全管家", "联通助理",
        "在中国联通 App 查询“联通安全管家”或“防骚扰”，也可通过“联通助理”公众号的安全管家入口查询。各地可能提供“手机管家”“反诈名片”等办理入口，请向 10010 确认本号码可用的防骚扰服务；增强版、智能代接等可能收费。",
        "10010", "https://vvm.wo116114.com/"),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CarrierProtectionPreferences() {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    fun open(intent: Intent) {
        try { context.startActivity(intent) }
        catch (_: android.content.ActivityNotFoundException) { Toast.makeText(context, "未找到可打开此入口的应用", Toast.LENGTH_SHORT).show() }
    }
    SettingsSection("网络侧拦截 + 本机规则") {
        Text("建议搭配所属运营商的防骚扰服务，补充号码分类和高频呼叫防护。", style = MaterialTheme.typography.bodyMedium)
        ExpandableHint("双层防护与白名单说明",
            "第一层由运营商在网络侧识别和拦截；第二层由静话处理到达手机的来电。两套设置独立：静话白名单只在本机生效，重要号码也应在运营商侧放行。运营商已拦截的电话通常不会到达静话，记录和通知请在运营商渠道查看。静话无法检测是否已开通服务。双卡请分别按号码所属运营商办理。")
    }
    carrierGuides.forEach { guide ->
        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(guide.carrier, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(guide.service, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { expanded = if (expanded == guide.carrier) null else guide.carrier },
                    contentPadding = PaddingValues(0.dp)) { Text(if (expanded == guide.carrier) "收起办理方式" else "查看办理方式") }
                AnimatedVisibility(expanded == guide.carrier) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(guide.steps, style = MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("官方入口名称", guide.entry))
                                Toast.makeText(context, "已复制：${guide.entry}", Toast.LENGTH_SHORT).show()
                            }) { Text("复制公众号名称") }
                            OutlinedButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse(guide.officialUrl))) }) { Text("官方说明") }
                            TextButton(onClick = { open(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", guide.customerService, null))) }) {
                                Text("咨询 ${guide.customerService}")
                            }
                        }
                    }
                }
            }
        }
    }
    SettingsSection("办理前确认") {
        Text("优先咨询免费基础防护；增强、AI 接听等增值功能可能收费。支持范围、资费和菜单以号码归属地运营商实际办理页面为准。本页仅提供推荐，不代办或自动订购。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("资料核对：2026-11-04。参考运营商官方介绍及通信管理局办理指南。",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
