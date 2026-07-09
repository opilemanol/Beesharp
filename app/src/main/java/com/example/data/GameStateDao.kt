package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GameStateDao {
    @Query("SELECT * FROM game_state WHERE id = 1")
    fun getGameStateFlow(): Flow<GameState?>

    @Query("SELECT * FROM game_state WHERE id = 1")
    suspend fun getGameState(): GameState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameState(gameState: GameState)
}
