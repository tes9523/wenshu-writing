package com.wenshu.writing.data.repository

import com.wenshu.writing.data.local.dao.ChatDao
import com.wenshu.writing.data.local.entity.ChatMessageEntity
import com.wenshu.writing.data.local.entity.ChatSessionEntity
import com.wenshu.writing.data.local.entity.toModel
import com.wenshu.writing.domain.model.ChatMessage
import com.wenshu.writing.domain.model.ChatRole
import com.wenshu.writing.domain.model.ChatSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChatRepository(
    private val chatDao: ChatDao
) {

    val sessions: Flow<List<ChatSession>> = chatDao.observeSessions().map { list ->
        list.map { it.toModel() }
    }

    fun observeMessages(sessionId: Long): Flow<List<ChatMessage>> =
        chatDao.observeMessages(sessionId).map { list -> list.map { it.toModel() } }

    suspend fun getMessages(sessionId: Long): List<ChatMessage> = withContext(Dispatchers.IO) {
        chatDao.getMessages(sessionId).map { it.toModel() }
    }

    suspend fun createSession(title: String, bookId: Long?): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        chatDao.insertSession(
            ChatSessionEntity(
                title = title.ifBlank { "新对话" },
                bookId = bookId,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun renameSession(sessionId: Long, title: String) = withContext(Dispatchers.IO) {
        chatDao.renameSession(sessionId, title, System.currentTimeMillis())
    }

    suspend fun appendMessage(
        sessionId: Long,
        role: ChatRole,
        content: String,
        isError: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = chatDao.insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = role.name,
                content = content,
                isError = isError,
                createdAt = now
            )
        )
        chatDao.touchSession(sessionId, now)
        id
    }

    /** 质检等后台流程需要把结果直接写入某个会话时使用。 */
    suspend fun appendAssistantMessage(sessionId: Long, content: String): Long =
        appendMessage(sessionId, ChatRole.ASSISTANT, content)

    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteMessages(sessionId)
        chatDao.deleteSession(sessionId)
    }

    suspend fun clearMessages(sessionId: Long) = withContext(Dispatchers.IO) {
        chatDao.deleteMessages(sessionId)
    }

    suspend fun getSessionsByBook(bookId: Long): List<ChatSession> = withContext(Dispatchers.IO) {
        chatDao.getSessionsByBook(bookId).map { it.toModel() }
    }

    suspend fun getSession(sessionId: Long): ChatSession? = withContext(Dispatchers.IO) {
        chatDao.getSession(sessionId)?.toModel()
    }

    suspend fun getLatestSession(): ChatSession? = withContext(Dispatchers.IO) {
        chatDao.getSessions().firstOrNull()?.toModel()
    }
}
