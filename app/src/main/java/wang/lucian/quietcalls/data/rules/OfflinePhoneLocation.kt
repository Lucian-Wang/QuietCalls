// 静话修改版（Lucian-Wang）：在 Pixel Telo 基础上修改；参见 UPSTREAM.md。
package wang.lucian.quietcalls.data.rules

import android.content.Context
import java.nio.ByteBuffer
import java.nio.ByteOrder
import wang.lucian.quietcalls.data.PhoneNumberNormalizer
import wang.lucian.quietcalls.data.remote.PhoneLocationInfo

/** 使用 MIT 许可 phone.dat 查询号码初始归属地，不代表实时位置或携号转网后的运营商。 */
object OfflinePhoneLocation {
    private data class Table(val bytes: ByteArray, val buffer: ByteBuffer, val offset: Int,
        val count: Int, val descriptions: Map<Int, PhoneLocationInfo>, val areas: Map<String, PhoneLocationInfo>)
    @Volatile private var table: Table? = null
    @Synchronized fun prepare(context: Context) {
        if (table != null) return
        val bytes = context.assets.open("phone.dat").use { it.readBytes() }
        require(bytes.size >= 8)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val offset = buffer.getInt(4)
        require(offset in 8..bytes.size && (bytes.size - offset) % 9 == 0)
        val descriptions = mutableMapOf<Int, PhoneLocationInfo>()
        val areas = mutableMapOf<String, PhoneLocationInfo>()
        var pos = 8
        while (pos < offset) {
            var end = pos
            while (end < offset && bytes[end] != 0.toByte()) end++
            val fields = String(bytes, pos, end-pos, Charsets.UTF_8).split('|')
            if (fields.size >= 4) {
                val location = PhoneLocationInfo(province = fields[0], city = fields[1])
                descriptions[pos] = location
                if (fields[3].isNotBlank()) areas[fields[3]] = location
            }
            pos = end + 1
        }
        table = Table(bytes, buffer, offset, (bytes.size-offset)/9, descriptions, areas)
    }
    fun regions(context: Context): List<String> {
        if (table == null) prepare(context)
        val values = table?.descriptions?.values.orEmpty()
        return (values.map { it.province } + values.map { "${it.province}/${it.city}" })
            .filter { it.isNotBlank() && !it.endsWith("/") }.distinct().sorted()
    }
    fun lookup(context: Context, number: String): PhoneLocationInfo? {
        if (CallRuleMatcher.foreignKind(number) != 0) return null
        if (table == null) runCatching { prepare(context) }.getOrNull() ?: return null
        val data = table ?: return null
        val phone = PhoneNumberNormalizer.normalizeForLookup(number)
        if (phone.length == 11 && phone.startsWith('1') && phone.all(Char::isDigit)) {
            val prefix = phone.take(7).toIntOrNull() ?: return null
            var low = 0; var high = data.count-1
            while (low <= high) {
                val mid = (low+high).ushr(1); val at = data.offset+mid*9
                val current = data.buffer.getInt(at)
                when {
                    current < prefix -> low = mid+1
                    current > prefix -> high = mid-1
                    else -> return data.descriptions[data.buffer.getInt(at+4)]
                }
            }
        }
        // 固话只接受带长途区号的完整号码，未知或不带区号时不猜测城市。
        if (phone.startsWith('0') && phone.length in 10..12 && phone.all(Char::isDigit)) {
            return data.areas[phone.take(3)] ?: data.areas[phone.take(4)]
        }
        return null
    }
}
