package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepository(private val gameStateDao: GameStateDao) {
    val gameStateFlow: Flow<GameState> = gameStateDao.getGameStateFlow().map { it ?: GameState() }

    suspend fun getGameState(): GameState {
        return gameStateDao.getGameState() ?: GameState()
    }

    suspend fun saveGameState(state: GameState) {
        gameStateDao.insertGameState(state)
    }
}
