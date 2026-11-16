// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls

import android.app.Instrumentation
import android.os.Bundle
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.koin.core.context.GlobalContext
import wang.lucian.quietcalls.data.query.QueryBackendProvider
import wang.lucian.quietcalls.data.remote.Direct360QueryApi
import wang.lucian.quietcalls.data.repository.QueryRepository
import wang.lucian.quietcalls.data.repository.QueryResponseFormatException

/** 设备验证：真实响应样本、异常边界和两个公开号码的实时直连；不修改用户配置。 */
fun verifyDirect360(instrumentation: Instrumentation) {
    val output = Bundle()
    try { runBlocking {
        fun fixture(name: String) = instrumentation.context.assets
            .open("direct360/$name.txt").bufferedReader().use { it.readText() }
        val marked = fixture("marked")
        val positive = Direct360QueryApi.parseResponse("4000395555", marked)
        check(positive.isSpam && positive.tag == "广告推销" && positive.source == "360")
        check(positive.feedbackToken == null)
        val empty = Direct360QueryApi.parseResponse("95588", "callback('')")
        check(!empty.isSpam && empty.tag.isBlank())
        val noTag = Direct360QueryApi.parseResponse("4008205555", fixture("unmarked"))
        check(!noTag.isSpam && noTag.tag.isBlank())
        listOf("快递送餐", "银行客服", "未知分类").forEach { label ->
            // 样本中的标签为 JSON 的普通中文字符串。
            val result = Direct360QueryApi.parseResponse("4000395555", marked.replace("广告推销", label))
            check(!result.isSpam && result.tag == label)
        }
        listOf(
            "<html>verification</html>", "callback({})", "callback({broken)",
            marked.replace("4000395555", "4000395556"),
            marked.replace("mobilecheck", "other"),
        ).forEach { invalid ->
            check(runCatching { Direct360QueryApi.parseResponse("4000395555", invalid) }
                .exceptionOrNull() is QueryResponseFormatException)
        }
        output.putString("fixtures", "PASS: marked, unmarked, empty, identity, unknown, malformed, mismatched")
        val koin = GlobalContext.get()
        val snapshot = koin.get<QueryBackendProvider>().snapshot() ?: error("Missing backend")
        check(snapshot.backendId == "direct360" && !snapshot.feedbackSupported)
        val repository = koin.get<QueryRepository>()
        check(repository.refreshSources().isSuccess)
        check(repository.sourceState.value.items.single().id == "360")
        val liveMarked = withTimeout(15000) { repository.queryNumber("4000395555") }
        check(liveMarked.backendId == "direct360" && !liveMarked.feedbackSupported)
        check(liveMarked.response.isSpam && liveMarked.response.tag == "广告推销")
        output.putString("live_marked", "PASS: direct360 marketing label")
        val liveEmpty = withTimeout(15000) { repository.queryNumber("95588") }
        check(!liveEmpty.response.isSpam && liveEmpty.response.tag.isBlank())
        output.putString("live_empty", "PASS: empty response is not failure")
    } } catch (error: Throwable) {
        // 不输出响应正文、号码或 URL。
        output.putString("error", error.javaClass.simpleName)
    }
    instrumentation.finish(if (output.containsKey("error")) 1 else 0, output)
}
