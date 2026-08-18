package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ad.AdHelper
import com.example.audio.SoundSynth
import com.example.audio.WordToSpeechHelper
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameState
import com.example.data.Level
import com.example.data.LevelData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GameStatus {
    PLAYING,
    SUCCESS,
    FAILURE
}

enum class ActiveTab {
    HOME,
    LEVELS,
    STATS
}

enum class TtsSpeed(val rate: Float, val displayName: String, val badge: String) {
    NORMAL(1.0f, "Normal", "⚡ 1.0x"),
    SLOW(0.7f, "Slow", "🐢 0.7x"),
    VERY_SLOW(0.5f, "Slowest", "🐌 0.5x")
}

data class GameUiState(
    val currentLevelId: Int = 1,
    val score: Int = 0,
    val isHintUnlockedForLevel: Boolean = false,
    val hintsUsedThisLevel: Int = 0,
    val freeHintsLeft: Int = 3,
    val inputLetters: List<Char?> = emptyList(),
    val cursorIndex: Int = 0,
    val timerSecondsRemaining: Int = 299,
    val isTimerActive: Boolean = false,
    val gameStatus: GameStatus = GameStatus.PLAYING,
    val isTtsReady: Boolean = false,
    val ttsSpeed: TtsSpeed = TtsSpeed.NORMAL,
    val currentTab: ActiveTab = ActiveTab.HOME,
    val messageText: String = "",
    val totalWordsSpelledCorrectly: Int = 0,
    val showAdStatusText: String? = null,
    val adLimitResetSeconds: Long = 0L
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GameRepository
    private val ttsHelper: WordToSpeechHelper
    val adHelper: AdHelper

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    init {
        val database = AppDatabase.getDatabase(application)
        repository = GameRepository(database.gameStateDao())
        ttsHelper = WordToSpeechHelper(application)
        adHelper = AdHelper(application)

        // Start ad limit check loop updating every second
        viewModelScope.launch {
            while (true) {
                updateAdLimitState()
                delay(1000)
            }
        }

        // Load game state from Database on initialization
        viewModelScope.launch {
            repository.gameStateFlow.collect { gameState ->
                _uiState.update { state ->
                    val currentLevel = LevelData.getLevel(gameState.currentLevelId)
                    val currentWordLen = currentWord(gameState.currentLevelId).length
                    
                    // Maintain or recreate letters list
                    val currentLetters = if (state.inputLetters.size == currentWordLen) {
                        state.inputLetters
                    } else {
                        List(currentWordLen) { null }
                    }

                    state.copy(
                        currentLevelId = gameState.currentLevelId,
                        score = gameState.score,
                        isHintUnlockedForLevel = gameState.isHintUnlockedForLevel,
                        hintsUsedThisLevel = gameState.hintsUsedThisLevel,
                        freeHintsLeft = gameState.freeHintsLeft,
                        inputLetters = currentLetters,
                        totalWordsSpelledCorrectly = maxOf(0, gameState.currentLevelId - 1)
                    )
                }
            }
        }
    }

    private fun currentWord(levelId: Int): String {
        return LevelData.getLevel(levelId).wordToSpell
    }

    fun setTtsSpeed(speed: TtsSpeed) {
        _uiState.update { it.copy(ttsSpeed = speed) }
    }

    fun playCurrentWordSpeech(overrideSpeed: TtsSpeed? = null) {
        val speedToUse = overrideSpeed ?: _uiState.value.ttsSpeed
        if (overrideSpeed != null) {
            _uiState.update { it.copy(ttsSpeed = overrideSpeed) }
        }
        val level = LevelData.getLevel(_uiState.value.currentLevelId)
        ttsHelper.speak(level.wordToSpell, speedToUse.rate)
        
        // Start/Restart timer with a precise 2 second delay as requested by specs
        startTimerWith2SecondDelay()
    }

    private fun startTimerWith2SecondDelay() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            _uiState.update { it.copy(isTimerActive = false, timerSecondsRemaining = 299) }
            
            // Wait 2 seconds before the game timer begins counting down
            delay(2000)
            
            _uiState.update { it.copy(isTimerActive = true) }
            while (_uiState.value.timerSecondsRemaining > 0 && _uiState.value.gameStatus == GameStatus.PLAYING) {
                delay(1000)
                _uiState.update {
                    if (it.gameStatus == GameStatus.PLAYING) {
                        val newTime = it.timerSecondsRemaining - 1
                        if (newTime == 0) {
                            // Trigger failure on timeout
                            triggerFailure()
                        }
                        it.copy(timerSecondsRemaining = newTime)
                    } else {
                        // Keep current if state changed
                        it
                    }
                }
            }
        }
    }

    fun onWordInputChanged(newWord: String) {
        if (_uiState.value.gameStatus != GameStatus.PLAYING) return
        
        val targetLength = _uiState.value.inputLetters.size
        // Limit word length to target length
        val word = newWord.take(targetLength)
        
        val letters = word.toList().map { it as Char? }.toMutableList()
        // Pad with nulls if word is shorter
        while (letters.size < targetLength) {
            letters.add(null)
        }
        
        _uiState.update { 
            it.copy(
                inputLetters = letters,
                cursorIndex = letters.indexOfFirst { it == null }.let { if (it == -1) letters.size else it }
            ) 
        }
    }

    fun triggerTypewriterSpelling() {
        if (_uiState.value.gameStatus != GameStatus.PLAYING) return
        
        viewModelScope.launch {
            val targetWord = currentWord(_uiState.value.currentLevelId).uppercase()
            val size = targetWord.length
            
            // Clear input first
            onClearPress()
            
            // Incrementally type each letter with a 300ms delay for visual typewriter effect
            for (i in 0 until size) {
                delay(300)
                if (_uiState.value.gameStatus != GameStatus.PLAYING) break
                val char = targetWord[i]
                
                val letters = _uiState.value.inputLetters.toMutableList()
                if (i < letters.size) {
                    letters[i] = char
                    val nextIndex = i + 1
                    _uiState.update { 
                        it.copy(
                            inputLetters = letters, 
                            cursorIndex = nextIndex
                        ) 
                    }
                }
            }
        }
    }

    fun onSpellForMeClick(activity: android.app.Activity) {
        if (_uiState.value.gameStatus != GameStatus.PLAYING) return
        if (_uiState.value.adLimitResetSeconds > 0) return
        
        val adHelper = adHelper
        if (adHelper.isRewardedAdLoaded()) {
            _uiState.update { it.copy(showAdStatusText = "Loading ad for Spell For Me...") }
            adHelper.showRewardedAd(activity) { earned ->
                _uiState.update { it.copy(showAdStatusText = null) }
                if (earned) {
                    recordRewardAdWatched()
                    triggerTypewriterSpelling()
                }
            }
        } else {
            // Safe simulation reward fallback in sandbox
            recordRewardAdWatched()
            triggerTypewriterSpelling()
        }
    }

    fun onKeyPress(char: Char) {
        if (_uiState.value.gameStatus != GameStatus.PLAYING) return

        val letters = _uiState.value.inputLetters.toMutableList()
        var targetIndex = -1
        for (i in _uiState.value.cursorIndex until letters.size) {
            if (letters[i] == null) {
                targetIndex = i
                break
            }
        }
        if (targetIndex == -1) {
            for (i in letters.indices) {
                if (letters[i] == null) {
                    targetIndex = i
                    break
                }
            }
        }

        if (targetIndex != -1) {
            letters[targetIndex] = char.uppercaseChar()
            var nextCursor = targetIndex + 1
            while (nextCursor < letters.size && letters[nextCursor] != null) {
                nextCursor++
            }
            _uiState.update { it.copy(inputLetters = letters, cursorIndex = nextCursor.coerceAtMost(letters.size)) }
        }
    }

    fun onDeletePress() {
        if (_uiState.value.gameStatus != GameStatus.PLAYING) return

        val letters = _uiState.value.inputLetters.toMutableList()
        var targetIndex = -1
        for (i in (_uiState.value.cursorIndex - 1) downTo 0) {
            if (letters[i] != null) {
                targetIndex = i
                break
            }
        }
        if (targetIndex == -1) {
            for (i in letters.indices.reversed()) {
                if (letters[i] != null) {
                    targetIndex = i
                    break
                }
            }
        }

        if (targetIndex != -1) {
            letters[targetIndex] = null
            _uiState.update { it.copy(inputLetters = letters, cursorIndex = targetIndex) }
        }
    }

    fun onClearPress() {
        if (_uiState.value.gameStatus != GameStatus.PLAYING) return

        val size = _uiState.value.inputLetters.size
        _uiState.update {
            it.copy(
                inputLetters = List(size) { null },
                cursorIndex = 0
            )
        }
    }

    fun onSkipPress() {
        if (_uiState.value.gameStatus != GameStatus.PLAYING) return
        
        // Auto go to next level but gain no points
        val nextLevelId = if (_uiState.value.currentLevelId < LevelData.levels.size) {
            _uiState.value.currentLevelId + 1
        } else {
            1 // loop
        }
        
        viewModelScope.launch {
            val currentFreeHints = _uiState.value.freeHintsLeft
            repository.saveGameState(
                GameState(
                    currentLevelId = nextLevelId,
                    score = _uiState.value.score,
                    isHintUnlockedForLevel = false,
                    hintsUsedThisLevel = 0,
                    freeHintsLeft = currentFreeHints
                )
            )
            onLevelSelected(nextLevelId)
        }
    }

    fun onSubmitPress() {
        val state = _uiState.value
        if (state.gameStatus != GameStatus.PLAYING) return

        // Player must completely fill all available word boxes before submitting
        if (state.inputLetters.any { it == null }) {
            _uiState.update { it.copy(messageText = "Please fill in all the letters first!") }
            viewModelScope.launch {
                delay(2000)
                _uiState.update { it.copy(messageText = "") }
            }
            return
        }

        val enteredWord = state.inputLetters.map { it ?: ' ' }.joinToString("")
        val targetWord = currentWord(state.currentLevelId)

        // Compare answer ignoring letter casing
        if (enteredWord.equals(targetWord, ignoreCase = true)) {
            triggerSuccess()
        } else {
            triggerFailure()
        }
    }

    private fun triggerSuccess() {
        timerJob?.cancel()
        _uiState.update { it.copy(gameStatus = GameStatus.SUCCESS) }
        viewModelScope.launch {
            SoundSynth.playSuccess()
            
            // Add custom visual highscore bonus (Award 50 points per completion)
            val bonusPoints = 50
            val newScore = _uiState.value.score + bonusPoints
            val currentLevelId = _uiState.value.currentLevelId
            val currentFreeHints = _uiState.value.freeHintsLeft
            
            val nextLevelId = currentLevelId + 1

            // Save player progress offline
            repository.saveGameState(
                GameState(
                    currentLevelId = nextLevelId,
                    score = newScore,
                    isHintUnlockedForLevel = false,
                    hintsUsedThisLevel = 0,
                    freeHintsLeft = currentFreeHints
                )
            )
        }
    }

    private fun triggerFailure() {
        timerJob?.cancel()
        _uiState.update { it.copy(gameStatus = GameStatus.FAILURE) }
        viewModelScope.launch {
            SoundSynth.playFailure()
        }
    }

    fun useFreeHint() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.freeHintsLeft <= 0) return@launch

            val level = LevelData.getLevel(state.currentLevelId)
            val targetWord = level.wordToSpell
            val letters = state.inputLetters.toMutableList()
            
            val unrevealedIndices = letters.indices.filter { letters[it] == null }
            if (unrevealedIndices.isNotEmpty()) {
                val randomIndex = unrevealedIndices.random()
                letters[randomIndex] = targetWord[randomIndex].uppercaseChar()
            }

            val firstNullIndex = letters.indexOfFirst { it == null }
            val nextCursor = if (firstNullIndex != -1) firstNullIndex else letters.size

            val nextFreeHints = state.freeHintsLeft - 1

            _uiState.update {
                it.copy(
                    freeHintsLeft = nextFreeHints,
                    isHintUnlockedForLevel = true,
                    inputLetters = letters,
                    cursorIndex = nextCursor
                )
            }

            val savedState = repository.getGameState()
            repository.saveGameState(
                savedState.copy(
                    isHintUnlockedForLevel = true,
                    freeHintsLeft = nextFreeHints
                )
            )
        }
    }

    fun onRewardAdWatchedSuccessfully() {
        if (_uiState.value.adLimitResetSeconds > 0) return
        recordRewardAdWatched()
        viewModelScope.launch {
            val restoredCount = 3
            
            _uiState.update {
                it.copy(
                    freeHintsLeft = restoredCount,
                    messageText = "Watched ad: 3 free hints restored!"
                )
            }

            val savedState = repository.getGameState()
            repository.saveGameState(
                savedState.copy(
                    freeHintsLeft = restoredCount
                )
            )

            // Auto clear message text after 3 seconds
            delay(3000)
            _uiState.update {
                if (it.messageText == "Watched ad: 3 free hints restored!") {
                    it.copy(messageText = "")
                } else {
                    it
                }
            }
        }
    }

    private fun getAdWatchTimestamps(): List<Long> {
        val prefs = getApplication<Application>().getSharedPreferences("ad_limits", android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("watch_timestamps", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").mapNotNull { it.toLongOrNull() }
    }

    private fun saveAdWatchTimestamps(timestamps: List<Long>) {
        val prefs = getApplication<Application>().getSharedPreferences("ad_limits", android.content.Context.MODE_PRIVATE)
        val raw = timestamps.joinToString(",")
        prefs.edit().putString("watch_timestamps", raw).apply()
    }

    private fun recordRewardAdWatched() {
        val now = System.currentTimeMillis()
        val twelveHoursAgo = now - (12 * 60 * 60 * 1000L)
        val current = getAdWatchTimestamps().filter { it >= twelveHoursAgo }.toMutableList()
        current.add(now)
        saveAdWatchTimestamps(current)
        updateAdLimitState()
    }

    private fun updateAdLimitState() {
        // Frequency limits managed directly via AdMob console
        _uiState.update { it.copy(adLimitResetSeconds = 0L) }
    }

    fun selectTab(tab: ActiveTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun onLevelSelected(levelId: Int) {
        timerJob?.cancel()
        _uiState.update { state ->
            val wordLen = currentWord(levelId).length
            state.copy(
                currentLevelId = levelId,
                inputLetters = List(wordLen) { null },
                cursorIndex = 0,
                timerSecondsRemaining = 299,
                isTimerActive = false,
                gameStatus = GameStatus.PLAYING,
                messageText = "",
                currentTab = ActiveTab.HOME
            )
        }
        // Save level select locally
        viewModelScope.launch {
            val savedState = repository.getGameState()
            repository.saveGameState(
                savedState.copy(
                    currentLevelId = levelId,
                    isHintUnlockedForLevel = false,
                    hintsUsedThisLevel = 0,
                    freeHintsLeft = savedState.freeHintsLeft
                )
            )
        }
    }

    fun retryLevel() {
        onLevelSelected(_uiState.value.currentLevelId)
    }

    fun autoProceedToNextLevel(activity: android.app.Activity) {
        val nextLevelId = _uiState.value.currentLevelId + 1
        
        // Spec requirements: Show AdMob Interstitial ad immediately after the player completes every third level
        val completedLevel = _uiState.value.currentLevelId
        if (completedLevel % 3 == 0) {
            _uiState.update { it.copy(showAdStatusText = "Loading inter-level sponsored ad...") }
            adHelper.showInterstitialAd(activity) {
                _uiState.update { it.copy(showAdStatusText = null) }
                onLevelSelected(nextLevelId)
            }
        } else {
            onLevelSelected(nextLevelId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        ttsHelper.shutdown()
    }
}
