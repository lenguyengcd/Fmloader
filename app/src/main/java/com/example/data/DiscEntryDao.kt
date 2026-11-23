package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscEntryDao {
  @Query("SELECT * FROM disc_entries ORDER BY addedAtMillis DESC")
  fun observeAllDiscs(): Flow<List<DiscEntryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertDisc(entry: DiscEntryEntity)

  @Query("DELETE FROM disc_entries WHERE uriString = :uriString")
  suspend fun deleteDisc(uriString: String)
}
