package io.github.xudong7587.sunnytv.source.clouddrive

import io.github.xudong7587.sunnytv.core.network.HttpPolicy
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.SAXParseException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.xml.parsers.SAXParserFactory

/** Pure JVM/Android DAV parser; bounded streaming state instead of a complete DOM. */
data class DavNode(val url: String, val name: String, val directory: Boolean, val bytes: Long?)

object DavXmlParser {
    const val MAX_XML_BYTES = 8 * 1024 * 1024
    const val MAX_ENTRIES = 10_000
    const val MAX_ELEMENTS = 200_000
    const val MAX_DEPTH = 32
    const val MAX_FIELD_CHARS = 16_384
    const val MAX_MARKUP_CHARS = 32_768

    fun parse(bytes: ByteArray, directory: String, root: String): List<DavNode> {
        require(bytes.size <= MAX_XML_BYTES) { "WebDAV 目录响应超过 8 MiB" }
        require(HttpPolicy.isScoped(directory, root)) { "目录超出已配置的 WebDAV 根路径" }
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        require(!text.contains('\u0000') && !Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            "WebDAV 返回了不允许的 XML 声明"
        }
        checkMarkupBudget(text)
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            // Android may not implement Xerces features; the UTF-8/DTD gate and resolver are mandatory.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        val handler = DavHandler(directory, root)
        factory.newSAXParser().xmlReader.apply {
            contentHandler = handler
            errorHandler = handler
            entityResolver = handler
        }.parse(InputSource(StringReader(text)))
        return handler.nodes.values.toList()
    }

    /** Bound a single start tag before SAX can allocate its attribute/namespace table. */
    private fun checkMarkupBudget(text: String) {
        var cursor = 0
        while(true) {
            val start = text.indexOf('<',cursor)
            if(start < 0) return
            val terminator = when {
                text.startsWith("<!--",start) -> "-->"
                text.startsWith("<![CDATA[",start) -> "]]>"
                else -> null
            }
            if(terminator != null) {
                val end = text.indexOf(terminator,start+2)
                require(end >= 0 && end-start <= MAX_MARKUP_CHARS) {"WebDAV XML 标记过长或未闭合"}
                cursor = end+terminator.length
            } else {
                var quote: Char? = null
                var end = start+1
                while(end < text.length) {
                    require(end-start <= MAX_MARKUP_CHARS) {"WebDAV XML 标记过长"}
                    val c=text[end]
                    if(quote != null) {if(c==quote) quote=null}
                    else if(c=='\'' || c=='"') quote=c
                    else if(c=='>') break
                    end++
                }
                require(end < text.length) {"WebDAV XML 标记未闭合"}
                cursor=end+1
            }
        }
    }

    private class DavHandler(val directory: String, val root: String) : DefaultHandler() {
        val nodes = linkedMapOf<String, DavNode>()
        private val path = mutableListOf<String>()
        private var elements = 0
        private var responses = 0
        private var fieldDepth = 0
        private var field = ""
        private val value = StringBuilder()
        private var href = ""
        private var name: String? = null
        private var folder = false
        private var length: Long? = null
        private var success = false
        private var propName: String? = null
        private var propFolder = false
        private var propLength: Long? = null
        private var status = ""

        private fun at(vararg parts: String) = path == parts.toList()
        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            require(++elements <= MAX_ELEMENTS) { "WebDAV XML 元素过多" }
            require(path.size < MAX_DEPTH) { "WebDAV XML 嵌套过深" }
            require(attrs.length <= 32 && (0 until attrs.length).all {attrs.getValue(it).length <= MAX_FIELD_CHARS}) {
                "WebDAV XML 属性过大"
            }
            path += if (uri == "DAV:") localName else "?"
            if (path.size == 1) require(at("multistatus")) { "此地址没有返回 WebDAV multistatus，请检查是否填成管理页面" }
            if (at("multistatus", "response")) {
                require(++responses <= MAX_ENTRIES) { "单目录条目过多，请拆分文件夹后重试" }
                href = ""; name = null; folder = false; length = null; success = false
            }
            if (at("multistatus", "response", "propstat")) {
                propName = null; propFolder = false; propLength = null; status = ""
            }
            if (at("multistatus", "response", "propstat", "prop", "resourcetype", "collection")) propFolder = true
            if (at("multistatus", "response", "href") ||
                at("multistatus", "response", "propstat", "status") ||
                at("multistatus", "response", "propstat", "prop", "displayname") ||
                at("multistatus", "response", "propstat", "prop", "getcontentlength")) {
                field = localName; fieldDepth = path.size; value.setLength(0)
            }
        }
        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (fieldDepth == 0) return
            require(value.length + length <= MAX_FIELD_CHARS) { "WebDAV 属性文本过长" }
            value.append(ch, start, length)
        }
        override fun endElement(uri: String, localName: String, qName: String) {
            if (fieldDepth == path.size) {
                val text = value.toString()
                when (field) {
                    "href" -> href = text.trim()
                    "displayname" -> propName = text.takeIf {it.isNotBlank()}
                    "getcontentlength" -> propLength = text.trim().toLongOrNull()?.takeIf {it >= 0}
                    "status" -> status = text.trim()
                }
                fieldDepth = 0; value.setLength(0)
            }
            if (at("multistatus", "response", "propstat") && status.split(Regex("\\s+")).getOrNull(1) == "200") {
                success = true; name = name ?: propName; folder = folder || propFolder; length = length ?: propLength
            }
            if (at("multistatus", "response")) finishResponse()
            path.removeAt(path.lastIndex)
        }
        private fun finishResponse() {
            if (!success || href.isBlank()) return
            val target = runCatching {HttpPolicy.resolveReference(directory, href)}.getOrNull() ?: return
            if (!HttpPolicy.isScoped(target, root) || target.trimEnd('/') == directory.trimEnd('/')) return
            val uri = HttpPolicy.validate(target)
            val finalUrl = if (folder && uri.rawQuery == null) target.trimEnd('/') + "/" else target
            val encodedName = uri.rawPath.orEmpty().trimEnd('/').substringAfterLast('/')
            val fallback = runCatching {URLDecoder.decode(encodedName.replace("+", "%2B"), "UTF-8")}.getOrDefault(encodedName)
            nodes.putIfAbsent(finalUrl, DavNode(finalUrl, name ?: fallback.ifBlank {"未命名"}, folder, length))
        }
        override fun resolveEntity(publicId: String?, systemId: String?): InputSource = throw SAXException("External XML entities are disabled")
        override fun error(e: SAXParseException) { throw e }
        override fun fatalError(e: SAXParseException) { throw e }
    }
}
