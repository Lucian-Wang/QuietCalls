// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.data.rules

import android.content.SharedPreferences
import wang.lucian.quietcalls.data.PhoneNumberRuleMatcher

/** 独立规则配置；所有规则默认关闭，集团短号默认允许。 */
data class CallRuleSettings(
    val blockInternational: Boolean = false,
    val blockHongKongMacaoTaiwan: Boolean = false,
    val blockStrangers: Boolean = false,
    val blockMarketing: Boolean = false,
    val allowInternal: Boolean = true,
    val allowedNumbers: String = "",
    val allowedRegions: String = "",
    val blockVirtualOperator: Boolean = false,
    val block400: Boolean = false,
    val block95: Boolean = false,
    val block96: Boolean = false,
) {
    companion object {
        fun read(prefs: SharedPreferences) = CallRuleSettings(
            prefs.getBoolean("rule_international", false),
            prefs.getBoolean("rule_hmt", false),
            prefs.getBoolean("rule_strangers", false),
            prefs.getBoolean("rule_marketing", false),
            prefs.getBoolean("rule_internal", true),
            prefs.getString("rule_allowed_numbers", "").orEmpty(),
            prefs.getString("rule_allowed_regions", "").orEmpty(),
            prefs.getBoolean("rule_virtual", false),
            prefs.getBoolean("rule_400", false),
            prefs.getBoolean("rule_95", false),
            prefs.getBoolean("rule_96", false),
        )
    }
    fun save(prefs: SharedPreferences) { prefs.edit()
        .putBoolean("rule_international", blockInternational)
        .putBoolean("rule_hmt", blockHongKongMacaoTaiwan)
        .putBoolean("rule_strangers", blockStrangers)
        .putBoolean("rule_marketing", blockMarketing)
        .putBoolean("rule_internal", allowInternal)
        .putString("rule_allowed_numbers", allowedNumbers)
        .putString("rule_allowed_regions", allowedRegions)
        .putBoolean("rule_virtual", blockVirtualOperator)
        .putBoolean("rule_400", block400)
        .putBoolean("rule_95", block95)
        .putBoolean("rule_96", block96).apply() }
}

/** 纯本地匹配：短号要求原始号码为 6 开头的 3–5 位数字，绝不退化为前缀放行。 */
object CallRuleMatcher {
    /** 只去掉中国国家码；不将其他国家号码误认为国内特殊号段。 */
    fun specialBlock(number: String, settings: CallRuleSettings): String? {
        val compact = number.trim().filterNot { it.isWhitespace() || it in "-()" }
        val domestic = when {
            compact.startsWith("+86") -> compact.drop(3)
            compact.startsWith("0086") -> compact.drop(4)
            compact.startsWith('+') || compact.startsWith("00") -> return null
            else -> compact
        }
        if (domestic.isEmpty() || domestic.any { it !in '0'..'9' }) return null
        return when {
            settings.blockVirtualOperator && domestic.length == 11 && domestic.take(3) in setOf("162", "165", "167", "170", "171") -> "虚拟运营商号段"
            settings.block400 && domestic.startsWith("400") && domestic.length == 10 -> "400 服务号码"
            settings.block95 && domestic.startsWith("95") && domestic.length in 5..20 -> "95 服务号码"
            settings.block96 && domestic.startsWith("96") && domestic.length in 5..20 -> "96 服务号码"
            else -> null
        }
    }
    fun internalNumber(number: String): Boolean {
        val compact = number.trim().filterNot { it.isWhitespace() || it in "-()" }
        return compact.length in 3..5 && compact.startsWith('6') && compact.all { it in '0'..'9' }
    }
    fun entries(value: String): List<String> = value.split(Regex("[,，;；\\n]+"))
        .map(String::trim).filter(String::isNotEmpty).distinct()
    fun allowedNumber(number: String, rules: String): Boolean {
        val candidates = PhoneNumberRuleMatcher.matchCandidates(number).toSet()
        return entries(rules).any { rule ->
            PhoneNumberRuleMatcher.isValidRule(rule) &&
                PhoneNumberRuleMatcher.matchCandidates(rule).any(candidates::contains)
        }
    }
    fun foreignKind(number: String): Int {
        val compact = number.trim().filterNot { it.isWhitespace() || it in "-()" }
        val international = when {
            compact.startsWith('+') -> compact.drop(1)
            compact.startsWith("00") -> compact.drop(2)
            else -> return 0
        }
        if (international.isBlank() || international.any { it !in '0'..'9' }) return 0
        return when {
            international.startsWith("852") || international.startsWith("853") || international.startsWith("886") -> 2
            international.startsWith("86") -> 0
            else -> 1
        }
    }
    fun marketing(label: String): Boolean = listOf("推销", "营销", "推广", "广告", "telemarket", "sales call")
        .any { label.contains(it, ignoreCase = true) }
    private fun clean(value: String): String = value.trim().removeSuffix("省").removeSuffix("市")
    fun allowedRegion(province: String, city: String, rules: String): Boolean = entries(rules).any { raw ->
        val parts = raw.split('/').map(String::trim)
        when {
            parts.size == 2 -> clean(parts[0]) == clean(province) && clean(parts[1]) == clean(city)
            parts.size != 1 -> false
            raw.endsWith("省") -> clean(raw) == clean(province)
            raw.endsWith("市") -> clean(raw) == clean(city)
            else -> clean(raw) == clean(city) || clean(raw) == clean(province)
        }
    }
}
