package com.helix.browser.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(history: History)

    @Query("SELECT * FROM history ORDER BY visitTime DESC")
    suspend fun getAll(): List<History>

    @Query("DELETE FROM history")
    suspend fun clearAll()

    // Optional: delete older than X days
}
