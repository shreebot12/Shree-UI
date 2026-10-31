package com.islandx.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "gesture_profiles")
data class GestureProfile(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean = false,
)

@Entity(tableName = "gesture_rules", primaryKeys = ["profileId", "trigger"])
data class GestureRule(
    val profileId: String,
    val trigger: String,
    val action: String,
    val enabled: Boolean = true,
)

@Dao
interface GestureDao {
    @Query("SELECT * FROM gesture_profiles ORDER BY name")
    fun observeProfiles(): Flow<List<GestureProfile>>

    @Query("SELECT * FROM gesture_rules WHERE profileId = :profileId ORDER BY trigger")
    fun observeRules(profileId: String): Flow<List<GestureRule>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: GestureProfile)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRule(rule: GestureRule)
}

@Database(entities = [GestureProfile::class, GestureRule::class], version = 1, exportSchema = false)
abstract class GestureDatabase : RoomDatabase() {
    abstract fun gestures(): GestureDao
}
