package com.wenshu.writing.data.repository

import com.wenshu.writing.domain.model.Chapter
import com.wenshu.writing.domain.model.CharacterCard
import com.wenshu.writing.domain.model.MemoNote
import com.wenshu.writing.domain.model.Novel
import com.wenshu.writing.domain.model.WorldSetting
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 整书数据包读写失败时携带面向用户的提示。 */
class BookArchiveException(message: String) : IllegalArgumentException(message)

data class BookArchiveData(
    val novel: Novel,
    val chapters: List<Chapter>,
    val characters: List<CharacterCard>,
    val worldSettings: List<WorldSetting>,
    val memos: List<MemoNote>
)

/**
 * 整书数据包：一个 zip，内含 book.json，打包本书全部章节、人物卡、世界观与备忘录。
 * 导入时所有数据归属新建书稿的 bookId，不影响全局公共素材。
 */
object BookArchive {

    private const val BOOK_JSON = "book.json"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun write(
        output: OutputStream,
        novel: Novel,
        chapters: List<Chapter>,
        characters: List<CharacterCard>,
        worldSettings: List<WorldSetting>,
        memos: List<MemoNote>
    ) {
        val dto = BookDto(
            novel = NovelDto(novel.title, novel.description),
            chapters = chapters.map {
                ChapterDto(it.title, it.content, it.orderIndex)
            },
            characters = characters.map {
                CharacterDto(it.name, it.role, it.description)
            },
            worldSettings = worldSettings.map {
                WorldDto(it.category, it.title, it.content)
            },
            memos = memos.map { MemoDto(it.title, it.content) }
        )
        val payload = json.encodeToString(BookDto.serializer(), dto)
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(BOOK_JSON))
            zip.write(payload.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun read(input: InputStream): BookArchiveData {
        val text = try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                var content: String? = null
                while (entry != null) {
                    if (!entry.isDirectory && entry.name == BOOK_JSON) {
                        content = zip.readBytes().toString(Charsets.UTF_8)
                            .removePrefix("\uFEFF")
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                content
            }
        } catch (e: ZipException) {
            throw BookArchiveException("压缩包损坏，不是合法ZIP")
        } catch (e: Exception) {
            throw BookArchiveException("整书数据包读取失败：${e.message ?: "格式错误"}")
        } ?: throw BookArchiveException("不是有效的整书数据包：缺少 book.json")

        val dto = try {
            json.decodeFromString(BookDto.serializer(), text)
        } catch (e: Exception) {
            throw BookArchiveException("book.json 格式错误")
        }
        if (dto.novel.title.isBlank()) {
            throw BookArchiveException("整书数据包缺少书名")
        }

        return BookArchiveData(
            novel = Novel(title = dto.novel.title, description = dto.novel.description),
            chapters = dto.chapters.map {
                Chapter(
                    title = it.title,
                    content = it.content,
                    orderIndex = it.orderIndex
                )
            },
            characters = dto.characters.map {
                CharacterCard(name = it.name, role = it.role, description = it.description)
            },
            worldSettings = dto.worldSettings.map {
                WorldSetting(category = it.category, title = it.title, content = it.content)
            },
            memos = dto.memos.map {
                MemoNote(title = it.title, content = it.content)
            }
        )
    }

    @Serializable
    private data class BookDto(
        val novel: NovelDto,
        val chapters: List<ChapterDto> = emptyList(),
        val characters: List<CharacterDto> = emptyList(),
        val worldSettings: List<WorldDto> = emptyList(),
        val memos: List<MemoDto> = emptyList()
    )

    @Serializable
    private data class NovelDto(
        val title: String,
        val description: String = ""
    )

    @Serializable
    private data class ChapterDto(
        val title: String,
        val content: String = "",
        val orderIndex: Int = 0
    )

    @Serializable
    private data class CharacterDto(
        val name: String,
        val role: String = "",
        val description: String = ""
    )

    @Serializable
    private data class WorldDto(
        val category: String = "",
        val title: String,
        val content: String = ""
    )

    @Serializable
    private data class MemoDto(
        val title: String = "",
        val content: String = ""
    )
}
