package com.dominion.mobile.data.local

import androidx.room.*

@Entity(tableName = "app_metadata")
data class MetadataRow(@PrimaryKey val id: Int = 1, val payload: String)

@Entity(tableName = "conversations", indices = [Index(value = ["updatedAt"])])
data class ConversationRow(@PrimaryKey val id: String, val position: Int, val updatedAt: Long, val title: String, val payload: String)

@Entity(
    tableName = "messages",
    primaryKeys = ["conversationId", "id"],
    foreignKeys = [ForeignKey(entity = ConversationRow::class, parentColumns = ["id"],
        childColumns = ["conversationId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["conversationId", "position"]), Index(value = ["createdAt"])]
)
data class MessageRow(val conversationId: String, val id: String, val position: Int, val createdAt: Long,
    val body: String, val payload: String)

@Entity(tableName = "local_records", primaryKeys = ["kind", "id"], indices = [Index(value = ["kind", "position"])])
data class RecordRow(val kind: String, val id: String, val position: Int, val payload: String)

@Dao
interface LocalDao {
    @Query("SELECT * FROM app_metadata WHERE id = 1") fun metadata(): MetadataRow?
    @Query("SELECT * FROM conversations ORDER BY position") fun conversations(): List<ConversationRow>
    @Query("SELECT * FROM messages ORDER BY conversationId, position") fun messages(): List<MessageRow>
    @Query("SELECT * FROM local_records ORDER BY kind, position") fun records(): List<RecordRow>
    @Query("SELECT * FROM messages WHERE instr(lower(body), lower(:query)) > 0 ORDER BY createdAt DESC LIMIT :limit")
    fun searchMessages(query: String, limit: Int): List<MessageRow>
    @Upsert fun putMetadata(row: MetadataRow)
    @Upsert fun putConversations(rows: List<ConversationRow>)
    @Upsert fun putMessages(rows: List<MessageRow>)
    @Upsert fun putRecords(rows: List<RecordRow>)
    @Delete fun deleteConversations(rows: List<ConversationRow>)
    @Delete fun deleteMessages(rows: List<MessageRow>)
    @Delete fun deleteRecords(rows: List<RecordRow>)
    @Query("DELETE FROM app_metadata") fun clearMetadata()
    @Query("DELETE FROM conversations") fun clearConversations()
    @Query("DELETE FROM local_records") fun clearRecords()
}

@Database(entities = [MetadataRow::class, ConversationRow::class, MessageRow::class, RecordRow::class],
    version = 1, exportSchema = false)
abstract class MobileDatabase : RoomDatabase() {
    abstract fun local(): LocalDao
}
