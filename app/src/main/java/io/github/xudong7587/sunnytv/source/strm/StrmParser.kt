package io.github.xudong7587.sunnytv.source.strm

import io.github.xudong7587.sunnytv.core.network.HttpPolicy
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object StrmParser {
    const val MAX_BYTES = 64 * 1024
    const val MAX_DEPTH = 3
    fun parse(bytes: ByteArray): String {
        require(bytes.size <= MAX_BYTES) { "STRM 文件超过 64 KiB" }
        val text = try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) { throw IllegalArgumentException("STRM 必须是有效的 UTF-8 文本") }
        val lines = text.removePrefix("\uFEFF").lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }.toList()
        require(lines.size == 1) { "STRM 需包含唯一的媒体地址，不接受多个地址或附加请求头" }
        return lines.single().also { HttpPolicy.validate(it) }
    }
    fun looksLikeStrm(url: String): Boolean =
        try { HttpPolicy.validate(url).path.orEmpty().endsWith(".strm", true) } catch (_: Exception) { false }
    fun checkRecursion(url: String, seen: Set<String>) {
        require(seen.size < MAX_DEPTH && url !in seen) { "STRM 嵌套过深或存在循环" }
    }
}
