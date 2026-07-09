package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_state")
data class GameState(
    @PrimaryKey val id: Int = 1,
    val currentLevelId: Int = 1,
    val score: Int = 0,
    val isHintUnlockedForLevel: Boolean = false,
    val hintsUsedThisLevel: Int = 0,
    val freeHintsLeft: Int = 3
)
