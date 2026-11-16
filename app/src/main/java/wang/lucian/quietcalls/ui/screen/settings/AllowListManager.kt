// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.ui.screen.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import wang.lucian.quietcalls.data.PhoneNumberRuleMatcher
import wang.lucian.quietcalls.data.rules.*
import wang.lucian.quietcalls.viewmodel.SettingViewModel

/** 复用已有存储格式，升级不丢失号码和地域条目；界面按条管理。 */
@Composable
fun AllowListManager(viewModel: SettingViewModel, region: Boolean, onDismiss: () -> Unit) {
    val rules = viewModel.callRules
    val entries = CallRuleMatcher.entries(if (region) rules.allowedRegions else rules.allowedNumbers)
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val numberFocus = remember { FocusRequester() }
    var add by remember { mutableStateOf(false) }
    LaunchedEffect(add) { if (add && !region) numberFocus.requestFocus() }
    var editing by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun save(values: List<String>) {
        val current = viewModel.callRules
        viewModel.updateCallRules(if (region) current.copy(allowedRegions = values.distinct().joinToString("\n"))
            else current.copy(allowedNumbers = values.distinct().joinToString("\n")))
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (region) "地域通行名单" else "号码通行名单") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (region) "仅豁免陌生人拦截；命中骚扰名单仍拦截。" else "精确号码优先放行，可逐条修改或删除。")
            if (entries.isEmpty()) Text("还没有添加条目", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(entries, key = { it }) { value ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(value.replace('/', '·'), Modifier.weight(1f).padding(vertical = 12.dp))
                        if (!region) IconButton(onClick = { editing = value; input = value; error = null; add = true }) {
                            Icon(Icons.Default.Edit, "编辑 $value")
                        }
                        IconButton(onClick = { pendingDelete = value }) { Icon(Icons.Default.DeleteOutline, "删除 $value") }
                    }
                    HorizontalDivider()
                }
            }
        } },
        confirmButton = { TextButton(onClick = { editing = null; input = ""; error = null; add = true }) { Text(if (region) "选择地域" else "添加号码") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
    pendingDelete?.let { value ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("移除通行条目？") },
            text = { Text("移除 $value 后，后续来电将按当前拦截规则处理。") },
            confirmButton = { TextButton(onClick = { save(entries - value); pendingDelete = null }) { Text("移除") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("取消") } })
    }
    if (add && region) RegionMultiPicker(entries, { add = false }) { values -> save(values); add = false }
    if (add && !region) AlertDialog(onDismissRequest = { add = false }, title = { Text(if (editing == null) "添加号码" else "编辑号码") },
        text = { Column {
            OutlinedTextField(input, { input = it; error = null }, modifier = Modifier.focusRequester(numberFocus), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), label = { Text("完整电话号码") }, singleLine = true, isError = error != null)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } }, confirmButton = { TextButton(onClick = {
            val number = PhoneNumberRuleMatcher.normalizeRuleForStorage(input)
            val current = CallRuleMatcher.entries(viewModel.callRules.allowedNumbers)
            when {
                !PhoneNumberRuleMatcher.isValidRule(input) -> error = "请输入有效的完整号码"
                number != editing && current.contains(number) -> error = "此号码已存在"
                editing == null && current.size >= 200 -> error = "最多添加 200 个号码"
                else -> { save(if (editing == null) current + number else current.map { if (it == editing) number else it }); add = false }
            }
        }) { Text("保存") } }, dismissButton = { TextButton(onClick = { add = false }) { Text("取消") } })
}

@Composable
fun RegionMultiPicker(initial: List<String>, onDismiss: () -> Unit,
    title: String = "选择允许的地域",
    explanation: String = "可选择省份或多个城市。地域通行仍检查骚扰名单。",
    onSave: (List<String>) -> Unit) {
    val context = LocalContext.current
    var catalog by remember { mutableStateOf<List<String>>(emptyList()) }
    var failed by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(initial.toSet()) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) { runCatching { OfflinePhoneLocation.regions(context) } }
        failed = result.isFailure
        catalog = result.getOrDefault(emptyList())
    }
    val visible = (initial + catalog).distinct().filter { it.contains(query.trim(), ignoreCase = true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$explanation 已选 ${selected.size} 项。")
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("搜索省份或城市") })
            if (failed) Text("地域数据加载失败，请稍后再试。", color = MaterialTheme.colorScheme.error)
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(visible, key = { it }) { value ->
                    fun toggle() { selected = if (value in selected) selected - value else if (selected.size < 200) selected + value else selected }
                    Row(Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 4.dp)) {
                        Checkbox(value in selected, { toggle() })
                        Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                            Text(value.replace('/', '·'))
                            if (!value.contains('/')) Text("整个省级地域", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } }, confirmButton = { TextButton(onClick = { onSave(selected.sorted()) }) { Text("保存选择") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
