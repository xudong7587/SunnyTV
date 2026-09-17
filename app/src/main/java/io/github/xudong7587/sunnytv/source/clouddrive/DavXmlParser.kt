package io.github.xudong7587.sunnytv.source.clouddrive

import io.github.xudong7587.sunnytv.core.network.HttpPolicy
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.StringReader
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.xml.parsers.DocumentBuilderFactory

/** Pure JVM/Android DAV parser. No network, Android API or credentials in this class. */
data class DavNode(val url: String, val name: String, val directory: Boolean, val bytes: Long?)

object DavXmlParser {
    const val MAX_XML_BYTES = 8 * 1024 * 1024
    const val MAX_ENTRIES = 10_000

    fun parse(bytes: ByteArray, directory: String, root: String): List<DavNode> {
        require(bytes.size <= MAX_XML_BYTES) { "WebDAV 目录响应超过 8 MiB" }
        require(HttpPolicy.isScoped(directory, root)) { "目录超出已配置的 WebDAV 根路径" }
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require(!text.contains('\u0000') && !Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            "WebDAV 返回了不允许的 XML 声明"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Some Android XML implementations do not support Xerces feature URIs.
            // The mandatory UTF-8/DTD gate and entity resolver remain active regardless.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw SAXException("External XML entities are disabled") }
            setErrorHandler(object : org.xml.sax.helpers.DefaultHandler() {
                override fun error(e: org.xml.sax.SAXParseException) { throw e }
                override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }
            })
        }
        val doc = builder.parse(InputSource(StringReader(text)))
        require(doc.documentElement.localName == "multistatus" && doc.documentElement.namespaceURI == "DAV:") {
            "此地址没有返回 WebDAV multistatus，请检查是否填成管理页面"
        }
        val responses = doc.getElementsByTagNameNS("DAV:", "response")
        require(responses.length <= MAX_ENTRIES) { "单目录条目过多，请拆分文件夹后重试" }
        val nodes = mutableListOf<DavNode>()
        for (index in 0 until responses.length) {
            val response = responses.item(index) as? Element ?: continue
            val href = response.first("href")?.textContent?.trim() ?: continue
            val target = runCatching { HttpPolicy.resolveReference(directory, href) }.getOrNull() ?: continue
            if (!HttpPolicy.isScoped(target, root) || target.trimEnd('/') == directory.trimEnd('/')) continue
            // Merge all successful propstat groups; ignore missing/forbidden properties.
            val props = response.getElementsByTagNameNS("DAV:", "propstat")
            var name: String? = null
            var folder = false
            var length: Long? = null
            var success = false
            for (p in 0 until props.length) {
                val propstat = props.item(p) as? Element ?: continue
                val code = propstat.first("status")?.textContent?.trim()?.split(Regex("\\s+"))?.getOrNull(1)?.toIntOrNull()
                if (code != 200) continue
                success = true
                val prop = propstat.first("prop") ?: continue
                name = name ?: prop.first("displayname")?.textContent?.takeIf { it.isNotBlank() }
                folder = folder || prop.getElementsByTagNameNS("DAV:", "collection").length > 0
                length = length ?: prop.first("getcontentlength")?.textContent?.toLongOrNull()?.takeIf { it >= 0 }
            }
            if (!success) continue
            val uri = HttpPolicy.validate(target)
            val finalUrl = if (folder && uri.rawQuery == null) target.trimEnd('/') + "/" else target
            val encodedName = uri.rawPath.orEmpty().trimEnd('/').substringAfterLast('/')
            val fallback = runCatching { URLDecoder.decode(encodedName.replace("+", "%2B"), "UTF-8") }.getOrDefault(encodedName)
            nodes += DavNode(finalUrl, name ?: fallback.ifBlank { "未命名" }, folder, length)
        }
        return nodes.distinctBy { it.url }
    }

    private fun Element.first(name: String): Element? =
        getElementsByTagNameNS("DAV:", name).item(0) as? Element
}
