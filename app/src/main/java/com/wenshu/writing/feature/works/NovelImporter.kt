package com.wenshu.writing.feature.works

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/** 导入失败：携带面向用户的精准原因。 */
class ImportException(val reason: String) : Exception(reason)

data class ImportedChapter(val title: String, val content: String)

/** 本地小说文档解析结果。 */
data class ParsedNovel(
    val title: String,
    val chapters: List<ImportedChapter>,
    val encoding: String,
    val formatName: String,
    /** 未识别到章节标题时整本归入单章「全文」。 */
    val singleChapter: Boolean
)

/**
 * 本地小说文档解析：支持 TXT / MD / DOCX，
 * 自动识别 UTF-8、UTF-16（BOM）、GBK / GB2312 编码，并按章节标题智能分章。
 */
object NovelImporter {

    /** 格式识别：扩展名 + 文件头双重判断，错误格式抛出精准原因。 */
    fun detectFormat(fileName: String, bytes: ByteArray): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "docx" || isZip(bytes)) return "docx"
        if (ext == "txt" || ext == "md") return ext
        throw ImportException(
            "不支持的文件格式" + (if (ext.isBlank()) "（无法识别扩展名）" else "：.$ext") +
                "，仅支持 TXT / MD / DOCX"
        )
    }

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() &&
            bytes[1] == 'K'.code.toByte()

    /** 完整解析：格式识别 → 解码 → 分章。 */
    fun parse(fileName: String, bytes: ByteArray): ParsedNovel {
        if (bytes.isEmpty()) throw ImportException("文件为空，未读取到任何内容")
        val format = detectFormat(fileName, bytes)
        val (text, encoding) = when (format) {
            "docx" -> extractDocxText(bytes) to "UTF-8（DOCX 内嵌）"
            else -> decodeText(bytes)
        }
        if (text.isBlank()) throw ImportException("文件为空，未读取到正文内容")
        val title = fileName.substringBeforeLast('.').trim().ifBlank { "未命名小说" }
        val chapters = splitChapters(text)
        return ParsedNovel(
            title = title,
            chapters = chapters.ifEmpty { listOf(ImportedChapter("全文", text.trim())) },
            encoding = encoding,
            formatName = format.uppercase(),
            singleChapter = chapters.isEmpty()
        )
    }

    /** 自动识别 UTF-8 / UTF-16（含 BOM）/ GBK（兼容 GB2312），返回文本与实际编码名。 */
    private fun decodeText(bytes: ByteArray): Pair<String, String> {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8) to "UTF-8"
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) to "UTF-16LE"
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE) to "UTF-16BE"
        }
        if (canDecodeStrict(bytes, Charsets.UTF_8)) {
            return String(bytes, Charsets.UTF_8) to "UTF-8"
        }
        val gbk = runCatching { String(bytes, Charset.forName("GBK")) }.getOrNull()
            ?: throw ImportException("编码无法识别（已尝试 UTF-8 / UTF-16 / GBK / GB2312）")
        return gbk to "GBK / GB2312"
    }

    private fun canDecodeStrict(bytes: ByteArray, charset: Charset): Boolean {
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes))
            true
        } catch (e: CharacterCodingException) {
            false
        }
    }

    /** 解析 DOCX：OOXML 本质是 zip，正文位于 word/document.xml（恒为 UTF-8）。 */
    private fun extractDocxText(bytes: ByteArray): String {
        val xmlBytes = runCatching {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") {
                        val out = ByteArrayOutputStream()
                        zip.copyTo(out)
                        return@use out.toByteArray()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                null
            }
        }.getOrElse {
            throw ImportException("DOCX 文件损坏或格式错误，无法读取正文")
        } ?: throw ImportException("DOCX 文件损坏：缺少 word/document.xml")

        var xml = String(xmlBytes, Charsets.UTF_8)
        xml = xml
            .replace(Regex("<w:tab\\s*/>"), "\t")
            .replace(Regex("<w:br\\s*/>"), "\n")
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("</w:tr>"), "\n")
            .replace(Regex("<[^>]+>"), "")
        return unescapeXml(xml)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
    }

    private fun unescapeXml(text: String): String = text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: ""
        }
        .replace("&amp;", "&")

    /** 章节标题：第X章/节/回/卷/部/篇、Chapter N、楔子/序章/尾声/番外，允许 Markdown 前缀。 */
    private val CHAPTER_HEADING = Regex(
        "(?m)^[ \\t]{0,4}(?:#{1,6}[ \\t]+)?(" +
            "第[0-9零一二三四五六七八九十百千两]{1,6}[章节回卷部篇][^\\n]{0,38}" +
            "|Chapter[ \\t]*\\d+[^\\n]{0,38}" +
            "|楔子|序章|序言|引子|尾声|后记|番外[^\\n]{0,20}" +
            ")[ \\t]*$"
    )

    /** 按章节标题切分；返回空列表表示未识别到章节。 */
    private fun splitChapters(text: String): List<ImportedChapter> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val matches = CHAPTER_HEADING.findAll(normalized).toList()
        if (matches.isEmpty()) return emptyList()

        val chapters = mutableListOf<ImportedChapter>()
        val preamble = normalized.substring(0, matches.first().range.first).trim()
        if (preamble.length >= 50) {
            chapters += ImportedChapter("卷首", preamble)
        }
        matches.forEachIndexed { index, match ->
            val start = match.range.first
            val end = if (index + 1 < matches.size) {
                matches[index + 1].range.first
            } else {
                normalized.length
            }
            val heading = match.groupValues[1].trim().take(40).ifBlank { "第 ${index + 1} 章" }
            val body = normalized.substring(start, end)
                .substringAfter('\n', "")
                .trim()
            chapters += ImportedChapter(heading, body)
        }
        return chapters
    }
}
