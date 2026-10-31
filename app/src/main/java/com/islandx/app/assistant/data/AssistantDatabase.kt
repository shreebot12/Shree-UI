package com.islandx.app.assistant.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.islandx.app.assistant.security.AssistantCipher
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "assistant_messages")
data class AssistantMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    val role: String,
    val encryptedContent: String,
    val encryptedToolCalls: String,
    val toolCallId: String?,
    val toolName: String?,
    val createdAt: Long,
)

@Entity(tableName = "assistant_memories")
data class AssistantMemoryEntity(
    @PrimaryKey val id: String,
    val encryptedText: String,
    val createdAt: Long,
)

@Entity(tableName = "assistant_skills")
data class AssistantSkillEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val encryptedInstructions: String,
    val enabled: Boolean,
    val createdAt: Long,
)

@Dao
interface AssistantDao {
    @Query("SELECT * FROM assistant_messages WHERE conversationId = :conversationId ORDER BY id DESC LIMIT 60")
    suspend fun recentMessages(conversationId: String): List<AssistantMessageEntity>

    @Insert
    suspend fun insertMessage(message: AssistantMessageEntity)

    @Query("DELETE FROM assistant_messages WHERE conversationId = :conversationId")
    suspend fun clearMessages(conversationId: String)

    @Query("SELECT * FROM assistant_memories ORDER BY createdAt DESC LIMIT 100")
    suspend fun memories(): List<AssistantMemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: AssistantMemoryEntity)

    @Query("DELETE FROM assistant_memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("SELECT * FROM assistant_skills ORDER BY name COLLATE NOCASE")
    suspend fun skills(): List<AssistantSkillEntity>

    @Query("SELECT * FROM assistant_skills WHERE enabled = 1 ORDER BY name COLLATE NOCASE LIMIT 10")
    suspend fun enabledSkills(): List<AssistantSkillEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSkill(skill: AssistantSkillEntity)

    @Query("DELETE FROM assistant_skills WHERE id = :id")
    suspend fun deleteSkill(id: String)
}

@Database(
    entities = [AssistantMessageEntity::class, AssistantMemoryEntity::class, AssistantSkillEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AssistantDatabase : RoomDatabase() {
    abstract fun assistantDao(): AssistantDao
}

data class AssistantToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class AssistantMessage(
    val role: String,
    val content: String,
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolCalls: List<AssistantToolCall> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class AssistantMemory(val id: String, val text: String, val createdAt: Long)

data class AssistantSkill(
    val id: String,
    val name: String,
    val description: String,
    val instructions: String,
    val enabled: Boolean,
    val createdAt: Long,
)

class AssistantStore(
    private val dao: AssistantDao,
    private val cipher: AssistantCipher,
) {
    suspend fun loadMessages(conversationId: String = DEFAULT_CONVERSATION): List<AssistantMessage> =
        dao.recentMessages(conversationId).asReversed().map { row ->
            AssistantMessage(
                role = row.role,
                content = cipher.decrypt(row.encryptedContent),
                toolCallId = row.toolCallId,
                toolName = row.toolName,
                toolCalls = decodeToolCalls(cipher.decrypt(row.encryptedToolCalls)),
                createdAt = row.createdAt,
            )
        }

    suspend fun appendMessage(message: AssistantMessage, conversationId: String = DEFAULT_CONVERSATION) {
        dao.insertMessage(
            AssistantMessageEntity(
                conversationId = conversationId,
                role = message.role,
                encryptedContent = cipher.encrypt(message.content),
                encryptedToolCalls = cipher.encrypt(encodeToolCalls(message.toolCalls)),
                toolCallId = message.toolCallId,
                toolName = message.toolName,
                createdAt = message.createdAt,
            ),
        )
    }

    suspend fun clearMessages(conversationId: String = DEFAULT_CONVERSATION) = dao.clearMessages(conversationId)

    suspend fun loadMemories(): List<AssistantMemory> = dao.memories().map {
        AssistantMemory(it.id, cipher.decrypt(it.encryptedText), it.createdAt)
    }

    suspend fun saveMemory(text: String): AssistantMemory {
        require(text.isNotBlank()) { "Memory cannot be empty." }
        require(text.length <= MAX_MEMORY_LENGTH) { "Memory must be $MAX_MEMORY_LENGTH characters or fewer." }
        val memory = AssistantMemory(UUID.randomUUID().toString(), text.trim(), System.currentTimeMillis())
        dao.saveMemory(AssistantMemoryEntity(memory.id, cipher.encrypt(memory.text), memory.createdAt))
        return memory
    }

    suspend fun deleteMemory(id: String) = dao.deleteMemory(id)

    suspend fun loadSkills(): List<AssistantSkill> = dao.skills().map(::decodeSkill)

    suspend fun loadEnabledSkills(): List<AssistantSkill> = dao.enabledSkills().map(::decodeSkill)

    suspend fun saveSkill(name: String, description: String, instructions: String): AssistantSkill {
        require(name.isNotBlank()) { "Skill name cannot be empty." }
        require(name.length <= MAX_SKILL_NAME_LENGTH) { "Skill names must be $MAX_SKILL_NAME_LENGTH characters or fewer." }
        require(instructions.isNotBlank()) { "Skill instructions cannot be empty." }
        require(instructions.length <= MAX_SKILL_INSTRUCTIONS_LENGTH) {
            "Skill instructions must be $MAX_SKILL_INSTRUCTIONS_LENGTH characters or fewer."
        }
        val skill = AssistantSkill(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim().take(MAX_SKILL_DESCRIPTION_LENGTH),
            instructions = instructions.trim(),
            enabled = true,
            createdAt = System.currentTimeMillis(),
        )
        dao.saveSkill(
            AssistantSkillEntity(
                skill.id,
                skill.name,
                skill.description,
                cipher.encrypt(skill.instructions),
                skill.enabled,
                skill.createdAt,
            ),
        )
        return skill
    }

    suspend fun setSkillEnabled(skill: AssistantSkill, enabled: Boolean) {
        dao.saveSkill(
            AssistantSkillEntity(
                skill.id,
                skill.name,
                skill.description,
                cipher.encrypt(skill.instructions),
                enabled,
                skill.createdAt,
            ),
        )
    }

    suspend fun deleteSkill(id: String) = dao.deleteSkill(id)

    private fun decodeSkill(row: AssistantSkillEntity) = AssistantSkill(
        row.id,
        row.name,
        row.description,
        cipher.decrypt(row.encryptedInstructions),
        row.enabled,
        row.createdAt,
    )

    private fun encodeToolCalls(calls: List<AssistantToolCall>): String = JSONArray().apply {
        calls.forEach { call ->
            put(JSONObject().put("id", call.id).put("name", call.name).put("arguments", call.arguments))
        }
    }.toString()

    private fun decodeToolCalls(encoded: String): List<AssistantToolCall> {
        val array = JSONArray(encoded)
        return (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            AssistantToolCall(
                id = item.getString("id"),
                name = item.getString("name"),
                arguments = item.getString("arguments"),
            )
        }
    }

    companion object {
        const val DEFAULT_CONVERSATION = "default"
        const val MAX_MEMORY_LENGTH = 2_000
        const val MAX_SKILL_NAME_LENGTH = 60
        const val MAX_SKILL_DESCRIPTION_LENGTH = 200
        const val MAX_SKILL_INSTRUCTIONS_LENGTH = 4_000
    }
}
