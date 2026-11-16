// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.data.remote

import android.text.Html
import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import wang.lucian.quietcalls.data.repository.QueryApiException
import wang.lucian.quietcalls.data.repository.QueryResponseFormatException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 默认实时查询直接访问 360；不执行 JSONP，不加载 HTML 内的任何外部资源。 */
class Direct360QueryApi(private val client: OkHttpClient) : QueryApi {
    override suspend fun getSources() = QuerySourcesResponse(
        defaultSources = listOf("360"),
        availableSources = listOf(QuerySource("360", 0)),
    )

    override suspend fun queryNumber(request: QueryRequest): QueryResponse {
        val number = normalize(request.number)
        if (!number.matches(Regex("[0-9]{3,20}"))) {
            throw QueryApiException(400, "Unsupported phone number")
        }
        val url = "https://open.onebox.so.com/dataApi".toHttpUrl().newBuilder()
            .addQueryParameter("query", number)
            .addQueryParameter("url", "mobilecheck")
            .addQueryParameter("num", "1")
            .addQueryParameter("type", "mobilecheck")
            .addQueryParameter("src", "onebox")
            .addQueryParameter("tpl", "1")
            .addQueryParameter("callback", "callback")
            .build()
        val call = client.newCall(Request.Builder().url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.so.com/")
            .build())
        // 取消外层超时必须同步取消 OkHttp，避免来电结束后请求继续运行。
        val body = suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use {
                            if (!it.isSuccessful) throw QueryApiException(it.code, "360 request failed")
                            val source = it.body.source()
                            source.request(MAX_BODY_BYTES + 1)
                            if (source.buffer.size > MAX_BODY_BYTES) throw QueryResponseFormatException()
                            source.readUtf8()
                        }
                        if (continuation.isActive) continuation.resume(text)
                    } catch (exception: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                }
            })
        }
        return parseResponse(number, body).copy(phone = request.number)
    }

    companion object {
        private const val MAX_BODY_BYTES = 512L * 1024
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private val json = Json { ignoreUnknownKeys = true }
        private val spamLabels = setOf(
            "广告推销", "广告营销", "推销", "骚扰电话", "骚扰", "诈骗电话", "诈骗",
            "疑似诈骗", "房产中介", "保险理财", "贷款理财", "招聘猎头", "响一声",
        )

        /** 未标记不等于安全；未知标签仅展示，禁止用任意非空文字触发拦截。 */
        fun parseResponse(number: String, body: String): QueryResponse {
            val match = Regex("^\\s*callback\\((.*)\\)\\s*;?\\s*$", RegexOption.DOT_MATCHES_ALL)
                .matchEntire(body) ?: throw QueryResponseFormatException()
            val payload = match.groupValues[1].trim()
            if (payload == "''" || payload == "\"\"") return unmarked(number)
            try {
                val root = json.parseToJsonElement(payload).jsonObject
                if (root["type"]?.jsonPrimitive?.content != "mobilecheck" ||
                    normalize(root["query"]?.jsonPrimitive?.content.orEmpty()) != normalize(number)) {
                    throw QueryResponseFormatException()
                }
                val html = root["html"]?.jsonPrimitive?.content ?: throw QueryResponseFormatException()
                // 同时验证卡片结构与卡片号码，拒绝验证页、搜索页、串号和格式变化。
                val details = elementText(html, "div", "mh-detail")
                    ?: elementText(html, "p", "mh-detail")
                    ?: throw QueryResponseFormatException()
                val displayed = Regex("[+0-9][0-9 ()-]{2,24}").find(details)?.value.orEmpty()
                if (normalize(displayed) != normalize(number)) throw QueryResponseFormatException()
                val tag = elementText(html, "span", "mohe-ph-mark").orEmpty().trim()
                if (tag.length > 80) throw QueryResponseFormatException()
                return unmarked(number).copy(tag = tag, isSpam = tag in spamLabels)
            } catch (exception: QueryResponseFormatException) {
                throw exception
            } catch (_: Exception) {
                throw QueryResponseFormatException()
            }
        }

        private fun unmarked(number: String) = QueryResponse(
            phone = number, isSpam = false, confidence = 0, source = "360",
            queryMode = "direct360", requestedSources = listOf("360"), effectiveSources = listOf("360"),
        )

        private fun elementText(html: String, element: String, className: String): String? {
            // 在起始标签中筛选 class，避免先匹配外层 div 而跳过内层号码卡片。
            val pattern = "<$element\\b[^>]*\\sclass\\s*=\\s*[\"'](?:[^\"']*\\s)?${Regex.escape(className)}(?:\\s[^\"']*)?[\"'][^>]*>(.*?)</$element\\s*>"
            val match = Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(html)
                ?: return null
            return Html.fromHtml(match.groupValues[1], Html.FROM_HTML_MODE_LEGACY)
                .toString().replace('\u00a0', ' ').trim()
        }

        private fun normalize(value: String): String {
            val compact = value.replace(Regex("[\\s()\\-]"), "")
            return when {
                compact.startsWith("+86") -> compact.drop(3)
                compact.startsWith("0086") -> compact.drop(4)
                else -> compact
            }
        }
    }
}
