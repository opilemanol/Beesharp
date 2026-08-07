package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ad.AdHelper
import com.example.data.Level
import com.example.data.LevelData
import com.example.ui.ActiveTab
import com.example.ui.GameStatus
import com.example.ui.GameUiState
import com.example.ui.TtsSpeed
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.notification.NotificationHelper
import com.example.ui.GameViewModel
import com.example.ui.theme.MyApplicationTheme
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: GameViewModel by viewModels()

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            NotificationHelper.schedule12HourReminder(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check and request notification permissions on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                NotificationHelper.schedule12HourReminder(this)
            }
        } else {
            NotificationHelper.schedule12HourReminder(this)
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    SpellingBeeGameScreen(
                        viewModel = viewModel,
                        activity = this@MainActivity,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun SpellingBeeGameScreen(
    viewModel: GameViewModel,
    activity: MainActivity,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current



    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF90CAF9)) // Light Blue sky fallback
    ) {
        // App background (Sunflowers and Bee theme Landscape)
        Image(
            painter = painterResource(id = R.drawable.img_bee_bg),
            contentDescription = "Spelling Bee Theme Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Status Bar info / Header (Removed as per user request)
            // SimulatedStatusBar()

            // 2. Playful Custom Navigation Bar tabs ('HOME', 'LEVELS', 'STATS')
            TabNavigationBar(
                activeTab = uiState.currentTab,
                onTabSelected = { viewModel.selectTab(it) }
            )

            // 3. Main Yellow Banner: Centered Orange Text with Aqua/Cyan borders
            YellowMainBanner()

            // Main body based on selected tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (uiState.currentTab) {
                    ActiveTab.HOME -> HomeGameView(viewModel = viewModel, uiState = uiState, activity = activity)
                    ActiveTab.LEVELS -> LevelsListView(
                        currentLevelId = uiState.currentLevelId,
                        onLevelClick = { level -> viewModel.onLevelSelected(level.levelId) }
                    )
                    ActiveTab.STATS -> StatsAchievementsView(uiState = uiState)
                }
            }

            // Keyboard and CTA buttons ONLY displayed when playing on home screen
            if (uiState.currentTab == ActiveTab.HOME) {
                // 4. Word boxes display input letters
                WordInputBoxesSection(
                    inputLetters = uiState.inputLetters
                )

                // 5. Hint Area
                HintButtonRow(
                    uiState = uiState,
                    onHintClick = {
                        if (uiState.freeHintsLeft > 0) {
                            viewModel.useFreeHint()
                        } else {
                            val adHelper = viewModel.adHelper
                            if (adHelper.isRewardedAdLoaded()) {
                                adHelper.showRewardedAd(activity) { earned ->
                                    if (earned) {
                                        viewModel.onRewardAdWatchedSuccessfully()
                                    }
                                }
                            } else {
                                // Fallback simulation if AdMob sandbox does not have loaded SDK
                                viewModel.onRewardAdWatchedSuccessfully()
                            }
                        }
                    }
                )

                // 6. Interactive QWERTY Keyboard
                CustomQwertyKeyboard(
                    onKey = { char -> viewModel.onKeyPress(char) },
                    onDelete = { viewModel.onDeletePress() }
                )

                // 7. System CTA Row (CLEAR, SPELL FOR ME, SUBMIT)
                MainCtaButtonsRow(
                    adLimitResetSeconds = uiState.adLimitResetSeconds,
                    onClear = { viewModel.onClearPress() },
                    onSpellForMe = { viewModel.onSpellForMeClick(activity) },
                    onSubmit = { viewModel.onSubmitPress() }
                )
            }

            // 8. Live Banner Ad View
            BannerAdView(adUnitId = viewModel.adHelper.BANNER_AD_ID)
        }

        // Action Overlays
        SuccessOverlay(
            visible = uiState.gameStatus == GameStatus.SUCCESS,
            levelId = uiState.currentLevelId,
            score = uiState.score,
            onClose = { viewModel.autoProceedToNextLevel(activity) }
        )

        FailureOverlay(
            visible = uiState.gameStatus == GameStatus.FAILURE,
            levelId = uiState.currentLevelId,
            onRetry = { viewModel.retryLevel() }
        )

        // Ad Status Toast / Overlay Banner
        uiState.showAdStatusText?.let { text ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(32.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFFFF6F00))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = text,
                            color = Color(0xFF4E342E),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Banner snackbar feedback toast
        AnimatedVisibility(
            visible = uiState.messageText.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4E342E)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = uiState.messageText,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SimulatedStatusBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(Color(0xFFB3E5FC)) // Light Blue
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "9:41 AM",
            color = Color(0xFF4E342E),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Battery icon
            Box(
                modifier = Modifier
                    .size(width = 16.dp, height = 9.dp)
                    .border(1.dp, Color(0xFF4E342E), RoundedCornerShape(2.dp))
                    .padding(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.8f)
                        .background(Color(0xFF4E342E))
                )
            }
            Text(
                text = "📶",
                fontSize = 10.sp,
                color = Color(0xFF4E342E)
            )
        }
    }
}

@Composable
fun TabNavigationBar(
    activeTab: ActiveTab,
    onTabSelected: (ActiveTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF90CAF9))
            .padding(vertical = 8.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val tabs = listOf(
            ActiveTab.HOME to "HOME",
            ActiveTab.LEVELS to "LEVELS",
            ActiveTab.STATS to "STATS"
        )
        tabs.forEach { (tab, label) ->
            val isActive = activeTab == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isActive) Color(0xFF4CAF50) else Color(0xFFFFFDE7))
                    .clickable { onTabSelected(tab) }
                    .border(2.dp, Color(0xFF8D6E63).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .testTag("tab_${label.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isActive) Color.White else Color(0xFF8D6E63),
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
fun YellowMainBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF7B3))
            .border(
                border = BorderStroke(2.dp, Color(0xFFA1DEDC)),
                shape = RoundedCornerShape(0.dp)
            )
            .padding(vertical = 8.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_app_icon),
                contentDescription = "App Icon",
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color(0xFFFF6F00), CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "BEE-RILLIANT... SPARKLE!",
                color = Color(0xFFFF6F00), // Solid orange
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun HomeGameView(
    viewModel: GameViewModel,
    uiState: GameUiState,
    activity: MainActivity
) {
    val level = LevelData.getLevel(uiState.currentLevelId)
    
    // Pulse animation for bee character
    val infiniteTransition = rememberInfiniteTransition(label = "bee_pulse")
    val beeTranslationY by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bee_y"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Space around Stats Panel Container
        Spacer(modifier = Modifier.height(8.dp))

        // Stats Overlay Panel (Rounded Brown Container)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(18.dp)),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF8D6E63)), // Medium Brown
            border = BorderStroke(3.dp, Color(0xFF5D4037)) // Darker Brown
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Internal stats subpanel (ROUND & SCORE)
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // ROUND
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFFDE7), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ROUND ",
                            color = Color(0xFF4E342E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "${uiState.currentLevelId}",
                            color = Color(0xFF8D6E63),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }

                    // SCORE
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFFDE7), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SCORE: ",
                            color = Color(0xFF4E342E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Text(
                            text = String.format("%,d", uiState.score),
                            color = Color(0xFF8D6E63),
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Timer Circle (TIME countdown helper)
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8D6E63))
                        .border(4.dp, Color(0xFFFFF7B3), CircleShape), // Yellow ring
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "TIME",
                            color = Color(0xFFFFF7B3),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                        val mm = uiState.timerSecondsRemaining / 60
                        val ss = uiState.timerSecondsRemaining % 60
                        Text(
                            text = String.format("%d:%02d", mm, ss),
                            color = Color(0xFFFFF7B3),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // LEVEL Badge block
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(Color(0xFFFFFDE7), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF8D6E63).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "LEVEL",
                            color = Color(0xFF4E342E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                        Text(
                            text = String.format("%02d", uiState.currentLevelId),
                            color = Color(0xFF8D6E63),
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }
                }
            }
        }

        // Active Play Section Container containing the cute bee and Speaker Play Button
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Floating smiling bee background character
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = 16.dp, y = 10.dp + beeTranslationY.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Small local vector emoji-like styling representing bee helper
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .shadow(2.dp, RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFFFC107))
                    ) {
                        Text(
                            text = "You can\ndo it!",
                            color = Color(0xFF4E342E),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xFFFFEB3B), CircleShape) // Bee body
                            .border(2.dp, Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("😊", fontSize = 16.sp) // Cute smiling bee face
                            Spacer(modifier = Modifier.height(1.dp))
                            // Simple stripes simulation
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(2.dp)
                                    .background(Color.Black)
                            )
                        }
                    }
                }
            }

            // Central Speaker "PLAY WORD" Button
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clickable { viewModel.playCurrentWordSpeech() }
                    .shadow(8.dp, CircleShape)
                    .background(Color(0xFFFFF7B3), CircleShape) // Bright Yellow
                    .border(5.dp, Color(0xFFA1DEDC), CircleShape) // Aqua outline
                    .testTag("play_word_button"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .background(Color(0xFFA1DEDC), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔊",
                            fontSize = 22.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "PLAY WORD",
                        color = Color(0xFF33691E),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = uiState.ttsSpeed.badge,
                        color = Color(0xFFE65100),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Clear Pronunciation Speed Controls (Normal vs. Slow vs. Slowest)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7).copy(alpha = 0.95f)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .border(1.5.dp, Color(0xFFFFB300), RoundedCornerShape(16.dp))
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🔊 Speed:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color(0xFF4E342E),
                    modifier = Modifier.padding(end = 4.dp)
                )

                TtsSpeed.values().forEach { speed ->
                    val isSelected = uiState.ttsSpeed == speed
                    Surface(
                        onClick = {
                            viewModel.playCurrentWordSpeech(speed)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFFFFD54F) else Color(0xFFFFFFFF),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFFE65100) else Color(0xFFD7CCC8)
                        ),
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .testTag("tts_speed_${speed.name.lowercase()}")
                    ) {
                        Text(
                            text = speed.badge,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF3E2723) else Color(0xFF5D4037),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun WordInputBoxesSection(
    inputLetters: List<Char?>
) {
    val wordFormatted = inputLetters.map { it?.toString() ?: "_" }.joinToString("  ")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF8BC34A).copy(alpha = 0.95f))
            .padding(top = 10.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "LISTEN & SPELL:",
            color = Color(0xFFFFFDE7),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .height(56.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(
                    width = 2.5.dp,
                    color = Color(0xFF5D4037),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = wordFormatted,
                color = Color(0xFF4E342E),
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun formatAdResetTime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "Resets in ${hours}h ${minutes}m"
        minutes > 0 -> "Resets in ${minutes}m ${secs}s"
        else -> "Resets in ${secs}s"
    }
}

@Composable
fun HintButtonRow(
    uiState: GameUiState,
    onHintClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF8BC34A).copy(alpha = 0.95f))
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val level = LevelData.getLevel(uiState.currentLevelId)
        val hasFreeHints = uiState.freeHintsLeft > 0
        val isAdLimitReached = uiState.adLimitResetSeconds > 0

        Button(
            onClick = {
                if (!hasFreeHints && isAdLimitReached) {
                    // Ad limit is reached and we need to watch ad - do nothing
                } else {
                    onHintClick()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (!hasFreeHints && isAdLimitReached) Color.Gray else (if (hasFreeHints) Color(0xFFFFF7B3) else Color(0xFFFFCC80)),
                contentColor = if (!hasFreeHints && isAdLimitReached) Color.White else Color(0xFF4E342E)
            ),
            border = BorderStroke(2.dp, if (!hasFreeHints && isAdLimitReached) Color.Gray else (if (hasFreeHints) Color(0xFF8D6E63).copy(alpha = 0.5f) else Color(0xFFE65100))),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            modifier = Modifier
                .height(38.dp)
                .testTag("hint_button")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hasFreeHints) {
                    Text(
                        text = "💡",
                        fontSize = 12.sp
                    )
                    Text(
                        text = "HINT",
                        color = Color(0xFF4E342E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF8D6E63), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = uiState.freeHintsLeft.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = if (isAdLimitReached) "🔒" else "📺",
                        fontSize = 12.sp
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isAdLimitReached) {
                            Text(
                                text = "LIMIT REACHED",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                            Text(
                                text = formatAdResetTime(uiState.adLimitResetSeconds),
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 7.sp
                            )
                        } else {
                            Text(
                                text = "GET 3 HINTS",
                                color = Color(0xFF5D4037),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Watch Ad",
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Bold,
                                fontSize = 7.sp
                            )
                        }
                    }
                }
            }
        }

        // Display the hint dialog summary description if hint is unlocked
        if (uiState.isHintUnlockedForLevel) {
            Spacer(modifier = Modifier.width(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFF8D6E63))
            ) {
                Text(
                    text = level.hintContext,
                    color = Color(0xFF4E342E),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CustomQwertyKeyboard(
    onKey: (Char) -> Unit,
    onDelete: () -> Unit
) {
    val keyboardRows = listOf(
        listOf('Q', 'W', 'E', 'R', 'T', 'Y', 'U', 'I', 'O', 'P'),
        listOf('A', 'S', 'D', 'F', 'G', 'H', 'J', 'K', 'L'),
        listOf('Z', 'X', 'C', 'V', 'B', 'N', 'M')
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFECEFF1)) // Slate theme background
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        keyboardRows.forEachIndexed { rowIdx, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                row.forEach { char ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF4E342E)) // Dark Brown keys
                            .clickable { onKey(char) }
                            .testTag("key_$char"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = char.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Add Backspace key next to row 3
                if (rowIdx == 2) {
                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF8D6E63)) // Dark Brown backspace
                            .clickable { onDelete() }
                            .testTag("key_delete"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "⌫",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "DEL",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainCtaButtonsRow(
    adLimitResetSeconds: Long,
    onClear: () -> Unit,
    onSpellForMe: () -> Unit,
    onSubmit: () -> Unit
) {
    val isAdLimitReached = adLimitResetSeconds > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // CLEAR button
        Button(
            onClick = onClear,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D)), // Gold
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .testTag("clear_button")
        ) {
            Text(
                text = "CLEAR",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
        }

        // SPELL FOR ME button with Watch Ad subscript
        Button(
            onClick = { if (!isAdLimitReached) onSpellForMe() },
            colors = ButtonDefaults.buttonColors(containerColor = if (isAdLimitReached) Color.Gray else Color(0xFFFF9800)), // Grey if limited
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
            modifier = Modifier
                .weight(1.3f)
                .height(44.dp)
                .testTag("spell_for_me_button")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isAdLimitReached) {
                    Text(
                        text = "LIMIT REACHED",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "🔒 " + formatAdResetTime(adLimitResetSeconds),
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "SPELL FOR ME",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "📺 Watch Ad",
                        color = Color.White.copy(alpha = 0.9f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        maxLines = 1
                    )
                }
            }
        }

        // SUBMIT button
        Button(
            onClick = onSubmit,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)), // Success Green
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1.3f)
                .height(44.dp)
                .testTag("submit_button")
        ) {
            Text(
                text = "SUBMIT",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun LevelsListView(
    currentLevelId: Int,
    onLevelClick: (Level) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color(0xFFFFFDE7).copy(alpha = 0.9f), RoundedCornerShape(18.dp))
            .border(2.dp, Color(0xFF8D6E63), RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Text(
            text = "SELECT LEVEL:",
            color = Color(0xFF4E342E),
            fontWeight = FontWeight.Black,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        )

        val displayedLevels = (1..maxOf(currentLevelId + 6, 21)).map { id ->
            LevelData.getLevel(id)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(displayedLevels) { level ->
                val isCompleted = level.levelId < currentLevelId
                val isActive = level.levelId == currentLevelId
                val isLocked = level.levelId > currentLevelId

                Box(
                    modifier = Modifier
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isActive -> Color(0xFF4CAF50)
                                isCompleted -> Color(0xFFFFF7B3)
                                else -> Color(0xFFE0E0E0).copy(alpha = 0.8f)
                            }
                        )
                        .border(
                            width = 2.dp,
                            color = if (isActive) Color(0xFFFFD700) else Color(0xFF8D6E63),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = !isLocked) { onLevelClick(level) }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Level",
                            color = if (isActive) Color.White else Color(0xFF4E342E),
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp
                        )
                        Text(
                            text = String.format("%02d", level.levelId),
                            color = if (isActive) Color.White else Color(0xFF8D6E63),
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        when {
                            isCompleted -> Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Finished",
                                tint = Color(0xFFFBC02D),
                                modifier = Modifier.size(14.dp)
                            )
                            isActive -> Text(
                                text = "PLAYING",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            else -> Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color.Gray,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsAchievementsView(uiState: GameUiState) {
    val currentStage = LevelData.getStageForLevel(uiState.currentLevelId)
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color(0xFFFFFDE7).copy(alpha = 0.9f), RoundedCornerShape(18.dp))
            .border(2.dp, Color(0xFF8D6E63), RoundedCornerShape(18.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "YOUR STATS & BADGES",
            color = Color(0xFFFF6F00),
            fontWeight = FontWeight.Black,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Stats card components
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF8D6E63))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total score
                StatsMetricRow(label = "TOTAL SCORE", value = String.format("%,d", uiState.score), icon = Icons.Filled.Star)
                // Words completed
                StatsMetricRow(label = "WORDS SPELLED", value = uiState.totalWordsSpelledCorrectly.toString(), icon = Icons.Filled.Check)
                // Dynamic Stage Info
                StatsMetricRow(label = "CURRENT STAGE", value = currentStage.stageName, icon = Icons.Filled.Face)
                // Dynamic Stage Progress Meter
                StatsMetricRow(
                    label = "STAGE MILESTONE",
                    value = if (currentStage.endLevel == Int.MAX_VALUE) "Beekeeping Legend" else "Level ${uiState.currentLevelId} / ${currentStage.endLevel}",
                    icon = Icons.Filled.Star
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "STAGES & MEDALS",
            color = Color(0xFF4E342E),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Earned level badge loops - now an elegant horizontal scrolling carousel of 10 stages
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(LevelData.stages) { stage ->
                val unlocked = uiState.currentLevelId >= stage.startLevel
                BadgeItem(
                    label = stage.stageName,
                    unlocked = unlocked,
                    description = stage.rangeText,
                    icon = stage.icon
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Notification settings box
        val context = LocalContext.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
            border = BorderStroke(1.dp, Color(0xFFFBC02D)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "🔔 Mastery Reminders Active",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037),
                    fontSize = 12.sp
                )
                Text(
                    text = "You will be reminded every 12 hours to test your spelling mastery of English words in BeeSharp.",
                    color = Color(0xFF795548),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        NotificationHelper.scheduleInstantTestReminder(context)
                        android.widget.Toast.makeText(context, "Test notification scheduled in 5 seconds!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("TRIGGER TEST NOW (5s)", color = Color(0xFF5D4037), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun StatsMetricRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFFFF7B3), modifier = Modifier.size(16.dp))
            Text(text = label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(text = value, color = Color(0xFFFFF7B3), fontWeight = FontWeight.Black, fontSize = 14.sp)
    }
}

@Composable
fun BadgeItem(label: String, unlocked: Boolean, description: String, icon: String) {
    Card(
        modifier = Modifier
            .width(115.dp)
            .height(115.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unlocked) Color(0xFFFFF7B3) else Color(0xFFE0E0E0).copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.5.dp, if (unlocked) Color(0xFFFFB300) else Color.Gray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = icon, fontSize = 28.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color(0xFF4E342E),
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                text = description,
                color = Color.Gray,
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                lineHeight = 10.sp
            )
        }
    }
}

@Composable
fun SuccessOverlay(
    visible: Boolean,
    levelId: Int,
    score: Int,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) +
                androidx.compose.animation.fadeIn(),
        exit = scaleOut() + androidx.compose.animation.fadeOut()
    ) {
        // Highscore overlay featuring gold medal & animated rotating icon
        val infiniteTransition = rememberInfiniteTransition(label = "medal_rotation")
        val rotationAngle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(4000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "medal_rotation"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)), // Off-white
                border = BorderStroke(4.dp, Color(0xFF4CAF50)) // Success bounds
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "CONGRATULATIONS!",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Gold medal simulation circle
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .rotate(rotationAngle)
                            .background(Color(0xFFFFD700), CircleShape) // Gold medal
                            .border(6.dp, Color(0xFFFFA000), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🏅", fontSize = 56.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "LEVEL $levelId CLEARED!",
                        color = Color(0xFF4E342E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+50 BONUS POINTS AWARDED!",
                        color = Color(0xFFE91E63), // Animated rose contrast color
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("success_continue_button")
                    ) {
                        Text(
                            text = "CONTINUE GAME  ▶",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FailureOverlay(
    visible: Boolean,
    levelId: Int,
    onRetry: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(animationSpec = spring()) + androidx.compose.animation.fadeIn(),
        exit = scaleOut() + androidx.compose.animation.fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7)),
                border = BorderStroke(4.dp, Color(0xFFD32F2F)) // Red outline
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "LEVEL $levelId FAILED!",
                        color = Color(0xFFD32F2F),
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "😢",
                        fontSize = 62.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "The word or timing was incorrect! Let's build your honeycomb skills with a retry.",
                        color = Color(0xFF4E342E),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("failure_retry_button")
                    ) {
                        Text(
                            text = "TRY AGAIN 🔄",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BannerAdView(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .background(Color.White.copy(alpha = 0.85f))
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                try {
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        setAdUnitId(adUnitId)
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        adListener = object : com.google.android.gms.ads.AdListener() {
                            override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                                Log.e("BannerAdView", "Banner ad failed to load (${error.code}): ${error.message}")
                            }
                            override fun onAdLoaded() {
                                Log.d("BannerAdView", "Banner ad loaded successfully")
                            }
                        }
                        loadAd(AdRequest.Builder().build())
                    }
                } catch (e: Exception) {
                    Log.e("BannerAdView", "Error creating AdView: ${e.message}", e)
                    android.view.View(context)
                }
            }
        )
    }
}
