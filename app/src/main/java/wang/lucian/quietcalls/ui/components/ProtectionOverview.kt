// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 简要状态默认展开一行，说明按需展开。 */
@Composable
fun ExpandableHint(title: String, detail: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
            Text(if (expanded) "$title · 收起" else "$title · 展开", style = MaterialTheme.typography.labelLarge)
        }
        AnimatedVisibility(expanded) {
            Text(detail, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 首屏保留当前状态和下一步，三项配置详情按需查看。 */
@Composable
fun ProtectionOverview(defaultApp: Boolean, permissionsReady: Boolean, databaseReady: Boolean,
    onDefaultApp: () -> Unit, onPermissions: () -> Unit, onDatabase: () -> Unit) {
    val ready = defaultApp && permissionsReady && databaseReady
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (ready) "防护配置已就绪" else "防护配置待完成", Modifier.weight(1f).padding(vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "收起" else "详情") }
            }
            AnimatedVisibility(expanded) {
                Text("系统接管：${if (defaultApp) "已开启" else "待设置"} · 必要权限：${if (permissionsReady) "已授予" else "待授权"}\n离线号码库：${if (databaseReady) "已就绪" else "待下载"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            if (!ready) TextButton(
                onClick = when { !defaultApp -> onDefaultApp; !permissionsReady -> onPermissions; else -> onDatabase },
                contentPadding = PaddingValues(0.dp),
            ) { Text(when { !defaultApp -> "设置来电拦截应用"; !permissionsReady -> "检查权限"; else -> "下载骚扰号码库" }) }
        }
    }
}
