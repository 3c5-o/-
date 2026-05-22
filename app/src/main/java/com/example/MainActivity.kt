package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDirection.Companion.Rtl
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.*
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            com.example.ui.theme.MyApplicationTheme {
                val context = LocalContext.current
                val database = remember { AppDatabase.getDatabase(context.applicationContext) }
                val repository = remember { SurahRepository(database.surahDao()) }
                val viewModel: SurahViewModel = viewModel(factory = SurahViewModelFactory(repository))
                
                QuranAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranAppScreen(viewModel: SurahViewModel) {
    val context = LocalContext.current
    val surahs by viewModel.filteredSurahs.collectAsState()
    val currentPlaying by viewModel.currentPlayingItem.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val positionMs by viewModel.currentPositionMs.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val speed by viewModel.playbackSpeed.collectAsState()
    val timerMinsLeft by viewModel.sleepTimerMinutesLeft.collectAsState()
    
    val activeFilter by viewModel.activeFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var isPlayerExpanded by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTimerDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            ImmersiveBottomNavigation(
                activeFilter = activeFilter,
                onSelectFilter = { viewModel.updateFilter(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            com.example.ui.theme.IslamicDarkBg,
                            com.example.ui.theme.IslamicDarkSurface
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (currentPlaying != null) 92.dp else 16.dp)
            ) {
                // Header Block - Immersive UI Hero Card
                HeaderBanner(
                    currentPlaying = currentPlaying,
                    playerState = playerState,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    onPlayPauseToggle = { viewModel.togglePlayPause() }
                )

                // Search Box with custom Arabic normalization support
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    placeholder = { 
                        Text(
                            "ابحث عن السورة...", 
                            color = com.example.ui.theme.SubText,
                            fontSize = 14.sp
                        ) 
                    },
                    leadingIcon = { 
                        Icon(
                            Icons.Default.Search, 
                            contentDescription = "Search", 
                            tint = com.example.ui.theme.IslamicGoldAccent
                        ) 
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    Icons.Default.Close, 
                                    contentDescription = "Clear", 
                                    tint = com.example.ui.theme.SubText
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = com.example.ui.theme.IslamicGoldAccent,
                        unfocusedBorderColor = com.example.ui.theme.IslamicDarkSurface,
                        focusedContainerColor = com.example.ui.theme.IslamicDarkSurface,
                        unfocusedContainerColor = com.example.ui.theme.IslamicDarkSurface,
                        focusedLabelColor = com.example.ui.theme.IslamicGoldAccent,
                        unfocusedLabelColor = com.example.ui.theme.LightText,
                        focusedTextColor = com.example.ui.theme.LightText,
                        unfocusedTextColor = com.example.ui.theme.LightText
                    ),
                    singleLine = true
                )

                // Category selection bar
                FilterTabsRow(activeFilter = activeFilter, onSelect = { viewModel.updateFilter(it) })

                // Sleep notification overlay
                if (timerMinsLeft != null) {
                    SleepTimerStatusBanner(timerMinsLeft!!, onStop = { viewModel.stopSleepTimer() })
                }

                // Surah Grid Row list
                if (surahs.isEmpty()) {
                    EmptySurahState()
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        itemsIndexed(
                            items = surahs,
                            key = { _, item -> item.surah.id }
                        ) { index, item ->
                            val isCurrent = currentPlaying?.surah?.id == item.surah.id
                            SurahCard(
                                item = item,
                                isPlaying = isCurrent,
                                onClick = { 
                                    viewModel.playSurah(surahs, index, context)
                                },
                                onFavoriteClick = { viewModel.toggleFavorite(item.surah.id) },
                                onDownloadClick = { 
                                    if (item.downloadStatus == SurahState.STATUS_DOWNLOADED) {
                                        viewModel.deleteDownload(context, item.surah.id)
                                    } else {
                                        viewModel.downloadSurah(context, item.surah.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Continuous floating mini player
            if (currentPlaying != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    MiniPlayer(
                        item = currentPlaying!!,
                        playerState = playerState,
                        progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                        onPlayPauseToggle = { viewModel.togglePlayPause() },
                        onExpand = { isPlayerExpanded = true }
                    )
                }
            }

            // Expanding sliding hud player
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessLow))
            ) {
                if (currentPlaying != null) {
                    FullscreenPlayer(
                        item = currentPlaying!!,
                        playerState = playerState,
                        currPosition = positionMs,
                        totalDuration = durationMs,
                        speed = speed,
                        repeatMode = repeatMode,
                        timerMins = timerMinsLeft,
                        onCollapse = { isPlayerExpanded = false },
                        onPlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.playNext(context) },
                        onPrev = { viewModel.playPrevious(context) },
                        onForward = { viewModel.skipForward() },
                        onBackward = { viewModel.skipBackward() },
                        onSeekStarted = { viewModel.seekStarted() },
                        onSeekFinished = { viewModel.seekTo(it) },
                        onSpeedClick = { showSpeedDialog = true },
                        onTimerClick = { showTimerDialog = true },
                        onRepeatClick = { viewModel.cycleRepeatMode() },
                        onFavoriteToggle = { viewModel.toggleFavorite(currentPlaying!!.surah.id) },
                        onDownloadAction = {
                            if (currentPlaying!!.downloadStatus == SurahState.STATUS_DOWNLOADED) {
                                viewModel.deleteDownload(context, currentPlaying!!.surah.id)
                            } else {
                                viewModel.downloadSurah(context, currentPlaying!!.surah.id)
                            }
                        }
                    )
                }
            }

            // Speech rate dialog overlay
            if (showSpeedDialog) {
                SpeedChoiceDialog(
                    currentSpeed = speed,
                    onSelectSpeed = {
                        viewModel.setPlaybackSpeed(it)
                        showSpeedDialog = false
                    },
                    onDismiss = { showSpeedDialog = false }
                )
            }

            // Sleep helper dial
            if (showTimerDialog) {
                TimerChoiceDialog(
                    onSelectTime = {
                        if (it == 0) viewModel.stopSleepTimer() else viewModel.startSleepTimer(it)
                        showTimerDialog = false
                    },
                    onDismiss = { showTimerDialog = false }
                )
            }
        }
    }
}

@Composable
fun HeaderBanner(
    currentPlaying: SurahWithState?,
    playerState: PlayerState,
    positionMs: Long,
    durationMs: Long,
    onPlayPauseToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF344458)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background spatial_audio_off styling (HTML accent representation)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier
                        .size(110.dp)
                        .alpha(0.12f),
                    tint = Color.White
                )
            }

            // Bottom subtle dark vignette gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFF0F1113).copy(alpha = 0.92f)
                            )
                        )
                    )
            )

            // Dynamic Content
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (currentPlaying != null) {
                        // "يستمع الآن" pill badge
                        Box(
                            modifier = Modifier
                                .background(com.example.ui.theme.IslamicGoldAccent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "يستمع الآن",
                                color = Color(0xFF1F2429),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "سورة " + currentPlaying.surah.nameAr,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                            color = com.example.ui.theme.SubText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        // Default offline branding status
                        Box(
                            modifier = Modifier
                                .background(com.example.ui.theme.IslamicGoldAccent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "القرآن الكريم",
                                color = Color(0xFF1F2429),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "المصحف المرتل",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "القارئ الشيخ ياسر الدوسري",
                            color = com.example.ui.theme.SubText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Play / Pause round-2xl action button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(com.example.ui.theme.IslamicGoldAccent)
                        .clickable { onPlayPauseToggle() },
                    contentAlignment = Alignment.Center
                ) {
                    if (playerState is PlayerState.Playing) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 4.dp, height = 16.dp)
                                    .background(Color(0xFF1A1C1E), shape = RoundedCornerShape(1.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .size(width = 4.dp, height = 16.dp)
                                    .background(Color(0xFF1A1C1E), shape = RoundedCornerShape(1.dp))
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color(0xFF1A1C1E),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterTabsRow(activeFilter: SurahFilter, onSelect: (SurahFilter) -> Unit) {
    val filters = listOf(
        Pair(SurahFilter.ALL, "الكل"),
        Pair(SurahFilter.FAVORITES, "المفضلة ❤️"),
        Pair(SurahFilter.DOWNLOADED, "المحملة 💾"),
        Pair(SurahFilter.MECCAN, "مكية"),
        Pair(SurahFilter.MEDINAN, "مدنية")
    )
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.forEach { (filterType, text) ->
            val isActive = activeFilter == filterType
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSelect(filterType) }
                    .background(
                        if (isActive) com.example.ui.theme.ActivePillColor
                        else com.example.ui.theme.IslamicDarkSurface
                    )
                    .border(
                        1.dp,
                        if (isActive) com.example.ui.theme.IslamicGoldAccent
                        else Color.Transparent,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = text,
                    color = if (isActive) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.LightText,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun SleepTimerStatusBanner(minsLeft: Int, onStop: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(com.example.ui.theme.IslamicDarkSurface, RoundedCornerShape(12.dp))
            .border(1.dp, com.example.ui.theme.IslamicGoldAccent.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CrescentMoonIcon(modifier = Modifier.size(18.dp), color = com.example.ui.theme.IslamicGoldAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "مؤقت النوم نشط: سيتم الإيقاف بعد $minsLeft دقيقة",
                    color = com.example.ui.theme.LightText,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "إلغاء",
                color = com.example.ui.theme.IslamicGoldAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onStop() }
            )
        }
    }
}

@Composable
fun EmptySurahState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = "Empty",
            modifier = Modifier.size(64.dp),
            tint = com.example.ui.theme.IslamicGoldAccent.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "لا توجد سور مطابقة لبحثك",
            color = com.example.ui.theme.LightText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "تأكد من كتابة الاسم بشكل صحيح أو تغيير الفلتر",
            color = com.example.ui.theme.SubText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RubElHizbBadge(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(42.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sSize = this.size.width
            val half = sSize / 2
            
            val path1 = Path().apply {
                moveTo(half, 0f)
                lineTo(sSize, half)
                lineTo(half, sSize)
                lineTo(0f, half)
                close()
            }
            val path2 = Path().apply {
                val offset = sSize * 0.146f
                moveTo(half, offset)
                lineTo(sSize - offset, half)
                lineTo(half, sSize - offset)
                lineTo(offset, half)
                close()
            }
            drawPath(path1, com.example.ui.theme.IslamicGoldAccent.copy(alpha = 0.8f), style = Stroke(width = 1.8.dp.toPx()))
            drawPath(path2, com.example.ui.theme.IslamicGoldAccent.copy(alpha = 0.8f), style = Stroke(width = 1.8.dp.toPx()))
        }
        Text(
            text = number.toString(),
            color = com.example.ui.theme.IslamicGoldAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
fun SurahCard(
    item: SurahWithState,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) com.example.ui.theme.ActivePillColor.copy(alpha = 0.35f)
                             else com.example.ui.theme.IslamicDarkSurface
        ),
        border = BorderStroke(
            1.dp,
            if (isPlaying) com.example.ui.theme.IslamicGoldAccent
            else com.example.ui.theme.IslamicDarkSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                RubElHizbBadge(number = item.surah.id)
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = item.surah.nameEn,
                        color = if (isPlaying) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.LightText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .background(com.example.ui.theme.IslamicDarkBg, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.surah.type,
                                color = com.example.ui.theme.IslamicGoldAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${item.surah.verses} آية",
                            color = com.example.ui.theme.SubText,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (item.downloadStatus) {
                        SurahState.STATUS_NOT_DOWNLOADED -> {
                            IconButton(onClick = onDownloadClick) {
                                ArrowDownIcon(modifier = Modifier.size(18.dp))
                            }
                        }
                        SurahState.STATUS_DOWNLOADING -> {
                            CircularProgressIndicator(
                                progress = item.downloadProgress / 100f,
                                modifier = Modifier.size(22.dp),
                                color = com.example.ui.theme.IslamicGoldAccent,
                                strokeWidth = 2.dp
                            )
                        }
                        SurahState.STATUS_DOWNLOADED -> {
                            IconButton(onClick = onDownloadClick) {
                                CheckCircleIcon(modifier = Modifier.size(20.dp))
                            }
                        }
                        SurahState.STATUS_FAILED -> {
                            IconButton(onClick = onDownloadClick) {
                                Icon(
                                    Icons.Default.Refresh, 
                                    contentDescription = "Retry", 
                                    tint = Color.Red,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (item.isFavorite) Color.Red else com.example.ui.theme.SubText,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = "سُورَة " + item.surah.nameAr,
                    color = if (isPlaying) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.LightText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    style = LocalTextStyle.current.copy(textDirection = Rtl),
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    item: SurahWithState,
    playerState: PlayerState,
    progress: Float,
    onPlayPauseToggle: () -> Unit,
    onExpand: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(12.dp, RoundedCornerShape(16.dp))
            .clickable { onExpand() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = com.example.ui.theme.IslamicDarkSurface),
        border = BorderStroke(1.dp, com.example.ui.theme.IslamicGoldAccent.copy(alpha = 0.4f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MainActivityContent.Companion.GoldenPlayButton(
                        isPlaying = playerState is PlayerState.Playing,
                        onClick = onPlayPauseToggle,
                        size = 42.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "سورة " + item.surah.nameAr,
                            color = com.example.ui.theme.IslamicGoldAccent,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (playerState is PlayerState.Preparing) "جاري التحميل..." else "ياسر الدوسري",
                            color = com.example.ui.theme.SubText,
                            fontSize = 11.sp,
                            textAlign = TextAlign.End,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(com.example.ui.theme.IslamicDarkBg, CircleShape)
                            .border(1.dp, com.example.ui.theme.IslamicGoldAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        RubElHizbBadge(number = item.surah.id, modifier = Modifier.size(30.dp))
                    }
                }
            }

            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(3.dp),
                color = com.example.ui.theme.IslamicGoldAccent,
                trackColor = Color.Transparent
            )
        }
    }
}

@Composable
fun FullscreenPlayer(
    item: SurahWithState,
    playerState: PlayerState,
    currPosition: Long,
    totalDuration: Long,
    speed: Float,
    repeatMode: QuranRepeatMode,
    timerMins: Int?,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onForward: () -> Unit,
    onBackward: () -> Unit,
    onSeekStarted: () -> Unit,
    onSeekFinished: (Long) -> Unit,
    onSpeedClick: () -> Unit,
    onTimerClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onDownloadAction: () -> Unit
) {
    var fileRotationAngle by remember { mutableStateOf(0f) }
    LaunchedEffect(playerState) {
        if (playerState is PlayerState.Playing) {
            while (isActive) {
                fileRotationAngle = (fileRotationAngle + 0.8f) % 360f
                delay(16)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.example.ui.theme.IslamicDarkBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Player Navigation Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(40.dp)
                        .background(com.example.ui.theme.IslamicDarkSurface, CircleShape)
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val w = this.size.width
                        val h = this.size.height
                        val p = Path().apply {
                            moveTo(0f, h*0.3f)
                            lineTo(w/2, h*0.85f)
                            lineTo(w, h*0.3f)
                        }
                        drawPath(p, com.example.ui.theme.IslamicGoldAccent, style = Stroke(width = 2.5.dp.toPx()))
                    }
                }

                Text(
                    text = "الآن للاستماع",
                    color = com.example.ui.theme.IslamicGoldAccent,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(40.dp)
                        .background(com.example.ui.theme.IslamicDarkSurface, CircleShape)
                ) {
                    Icon(
                        imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (item.isFavorite) Color.Red else com.example.ui.theme.IslamicGoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Central rotating gold disc
            Spacer(modifier = Modifier.height(16.dp))
            MainActivityContent.Companion.IslamicMandalaDisc(
                rotation = fileRotationAngle,
                isPlaying = playerState is PlayerState.Playing
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Text Segment Metadata
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "سورة " + item.surah.nameAr,
                    color = com.example.ui.theme.IslamicGoldAccent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.surah.nameEn,
                    color = com.example.ui.theme.LightText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(com.example.ui.theme.IslamicDarkSurface, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = item.surah.type,
                            color = com.example.ui.theme.IslamicGoldAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "ترتيبها: ${item.surah.id} • آياتها: ${item.surah.verses}",
                        color = com.example.ui.theme.SubText,
                        fontSize = 12.sp
                    )
                }
            }

            // Seek slider
            Column(modifier = Modifier.fillMaxWidth()) {
                val sliderPosition = remember(currPosition) { currPosition.toFloat() }
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        onSeekStarted()
                        onSeekFinished(it.toLong())
                    },
                    valueRange = 0f..(totalDuration.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(
                        thumbColor = com.example.ui.theme.IslamicGoldAccent,
                        activeTrackColor = com.example.ui.theme.IslamicGoldAccent,
                        inactiveTrackColor = com.example.ui.theme.IslamicDarkSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(currPosition),
                        color = com.example.ui.theme.SubText,
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatDuration(totalDuration),
                        color = com.example.ui.theme.SubText,
                        fontSize = 12.sp
                    )
                }
            }

            // Player main play button line HUD
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onBackward) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val w = this.size.width
                        val h = this.size.height
                        val p = Path().apply {
                            moveTo(w*0.8f, h*0.2f)
                            quadraticTo(w*0.2f, h*0.2f, w*0.2f, h*0.6f)
                            quadraticTo(w*0.2f, h*0.9f, w*0.8f, h*0.8f)
                        }
                        drawPath(p, com.example.ui.theme.SubText, style = Stroke(width = 2.dp.toPx()))
                        val arrow = Path().apply {
                            moveTo(w*0.1f, h*0.4f)
                            lineTo(w*0.35f, h*0.4f)
                            lineTo(w*0.2f, h*0.65f)
                            close()
                        }
                        drawPath(arrow, com.example.ui.theme.SubText)
                    }
                }

                MainActivityContent.Companion.GoldenNavButton(isNext = false, onClick = onPrev)

                MainActivityContent.Companion.GoldenPlayButton(
                    isPlaying = playerState is PlayerState.Playing,
                    onClick = onPlayPause,
                    size = 72.dp
                )

                MainActivityContent.Companion.GoldenNavButton(isNext = true, onClick = onNext)

                IconButton(onClick = onForward) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val w = this.size.width
                        val h = this.size.height
                        val p = Path().apply {
                            moveTo(w*0.2f, h*0.2f)
                            quadraticTo(w*0.8f, h*0.2f, w*0.8f, h*0.6f)
                            quadraticTo(w*0.8f, h*0.9f, w*0.2f, h*0.8f)
                        }
                        drawPath(p, com.example.ui.theme.SubText, style = Stroke(width = 2.dp.toPx()))
                        val arrow = Path().apply {
                            moveTo(w*0.9f, h*0.4f)
                            lineTo(w*0.65f, h*0.4f)
                            lineTo(w*0.8f, h*0.65f)
                            close()
                        }
                        drawPath(arrow, com.example.ui.theme.SubText)
                    }
                }
            }

            // Secondary option circle rows
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Repeat Indicator
                IconButton(
                    onClick = onRepeatClick,
                    modifier = Modifier
                        .size(46.dp)
                        .background(com.example.ui.theme.IslamicDarkSurface, CircleShape)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when (repeatMode) {
                                QuranRepeatMode.OFF -> Icons.Default.PlayArrow
                                QuranRepeatMode.ONE -> Icons.Default.Refresh
                                QuranRepeatMode.ALL -> Icons.Default.Refresh
                                QuranRepeatMode.SHUFFLE -> Icons.Default.Share
                            },
                            contentDescription = "Repeat",
                            tint = if (repeatMode != QuranRepeatMode.OFF) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.SubText,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when (repeatMode) {
                                QuranRepeatMode.OFF -> "إيقاف"
                                QuranRepeatMode.ONE -> "سورة"
                                QuranRepeatMode.ALL -> "الكل"
                                QuranRepeatMode.SHUFFLE -> "عشوائي"
                            },
                            color = if (repeatMode != QuranRepeatMode.OFF) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.SubText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Sleep Moon Dial
                IconButton(
                    onClick = onTimerClick,
                    modifier = Modifier
                        .size(46.dp)
                        .background(com.example.ui.theme.IslamicDarkSurface, CircleShape)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CrescentMoonIcon(
                            modifier = Modifier.size(16.dp),
                            color = if (timerMins != null) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.SubText
                        )
                        Text(
                            text = if (timerMins != null) "${timerMins}د" else "نوم",
                            color = if (timerMins != null) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.SubText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Playback speed rate slider
                IconButton(
                    onClick = onSpeedClick,
                    modifier = Modifier
                        .size(46.dp)
                        .background(com.example.ui.theme.IslamicDarkSurface, CircleShape)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SpeedometerIcon(
                            modifier = Modifier.size(16.dp),
                            color = if (speed != 1.0f) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.SubText
                        )
                        Text(
                            text = "${speed}x",
                            color = if (speed != 1.0f) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.SubText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Offline downloader actions from Player view
                IconButton(
                    onClick = onDownloadAction,
                    modifier = Modifier
                        .size(46.dp)
                        .background(com.example.ui.theme.IslamicDarkSurface, CircleShape)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val color = if (item.downloadStatus == SurahState.STATUS_DOWNLOADED) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.SubText
                        if (item.downloadStatus == SurahState.STATUS_DOWNLOADED) {
                            CheckCircleIcon(modifier = Modifier.size(16.dp), color = color)
                        } else {
                            ArrowDownIcon(modifier = Modifier.size(16.dp), color = color)
                        }
                        Text(
                            text = when (item.downloadStatus) {
                                SurahState.STATUS_DOWNLOADED -> "محملة"
                                SurahState.STATUS_DOWNLOADING -> "${item.downloadProgress}%"
                                else -> "تحميل"
                            },
                            color = color,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpeedChoiceDialog(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                "سرعة تشغيل الصوت", 
                color = com.example.ui.theme.IslamicGoldAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            ) 
        },
        containerColor = com.example.ui.theme.IslamicDarkSurface,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEach { option ->
                    val isSelected = option == currentSpeed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectSpeed(option) }
                            .background(
                                if (isSelected) com.example.ui.theme.ActivePillColor
                                else Color.Transparent
                            )
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${option}x" + (if (option == 1.0f) " (طبيعي)" else ""),
                            color = com.example.ui.theme.LightText,
                            fontSize = 15.sp
                        )
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check, 
                                contentDescription = "Active", 
                                tint = com.example.ui.theme.IslamicGoldAccent
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun TimerChoiceDialog(
    onSelectTime: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val times = listOf(
        Pair(0, "إيقاف مؤقت النوم"),
        Pair(5, "بعد ٥ دقائق"),
        Pair(15, "بعد ١٥ دقيقة"),
        Pair(30, "بعد ٣٠ دقيقة"),
        Pair(60, "بعد ٦٠ دقيقة (ساعة)"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "إعداد مؤقت النوم وموفر الطاقة",
                color = com.example.ui.theme.IslamicGoldAccent,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        containerColor = com.example.ui.theme.IslamicDarkSurface,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                times.forEach { (mins, text) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectTime(mins) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CrescentMoonIcon(modifier = Modifier.size(18.dp), color = com.example.ui.theme.IslamicGoldAccent)
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = text,
                            color = com.example.ui.theme.LightText,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
fun CrescentMoonIcon(
    modifier: Modifier = Modifier,
    color: Color = com.example.ui.theme.IslamicGoldAccent
) {
    Canvas(modifier = modifier) {
        val w = this.size.width
        val h = this.size.height
        
        val moonPath = Path().apply {
            moveTo(w * 0.7f, 0f)
            quadraticTo(w * 0.15f, h * 0.15f, w * 0.15f, h * 0.5f)
            quadraticTo(w * 0.15f, h * 0.85f, w * 0.7f, h)
            quadraticTo(w * 0.42f, h * 0.8f, w * 0.42f, h * 0.5f)
            quadraticTo(w * 0.42f, h * 0.2f, w * 0.7f, 0f)
            close()
        }
        drawPath(moonPath, color)
        
        val starPath = Path().apply {
            val cx = w * 0.75f
            val cy = h * 0.35f
            val r = w * 0.12f
            moveTo(cx, cy - r)
            lineTo(cx + r * 0.25f, cy - r * 0.25f)
            lineTo(cx + r, cy - r * 0.25f)
            lineTo(cx + r * 0.4f, cy + r * 0.1f)
            lineTo(cx + r * 0.6f, cy + r * 0.8f)
            lineTo(cx, cy + r * 0.35f)
            lineTo(cx - r * 0.6f, cy + r * 0.8f)
            lineTo(cx - r * 0.4f, cy + r * 0.1f)
            lineTo(cx - r, cy - r * 0.25f)
            lineTo(cx - r * 0.25f, cy - r * 0.25f)
            close()
        }
        drawPath(starPath, color)
    }
}

@Composable
fun SpeedometerIcon(modifier: Modifier = Modifier, color: Color = com.example.ui.theme.IslamicGoldAccent) {
    Canvas(modifier = modifier) {
        val w = this.size.width
        val h = this.size.height
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            style = Stroke(width = 1.5.dp.toPx())
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w / 2, h * 0.9f),
            end = androidx.compose.ui.geometry.Offset(w * 0.75f, h * 0.4f),
            strokeWidth = 2.dp.toPx()
        )
    }
}

@Composable
fun ArrowDownIcon(modifier: Modifier = Modifier, color: Color = com.example.ui.theme.IslamicGoldAccent) {
    Canvas(modifier = modifier) {
        val w = this.size.width
        val h = this.size.height
        val strokeW = 1.8.dp.toPx()
        drawLine(color, strokeWidth = strokeW, start = androidx.compose.ui.geometry.Offset(w/2, 0f), end = androidx.compose.ui.geometry.Offset(w/2, h*0.78f))
        drawLine(color, strokeWidth = strokeW, start = androidx.compose.ui.geometry.Offset(w*0.22f, h*0.48f), end = androidx.compose.ui.geometry.Offset(w/2, h*0.78f))
        drawLine(color, strokeWidth = strokeW, start = androidx.compose.ui.geometry.Offset(w*0.78f, h*0.48f), end = androidx.compose.ui.geometry.Offset(w/2, h*0.78f))
        drawLine(color, strokeWidth = strokeW, start = androidx.compose.ui.geometry.Offset(0f, h), end = androidx.compose.ui.geometry.Offset(w, h))
    }
}

@Composable
fun CheckCircleIcon(modifier: Modifier = Modifier, color: Color = com.example.ui.theme.IslamicGoldAccent) {
    Canvas(modifier = modifier) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(color = color.copy(alpha = 0.15f))
        drawCircle(color = color, style = Stroke(width = 1.5.dp.toPx()))
        val strokeW = 1.8.dp.toPx()
        drawLine(color, strokeWidth = strokeW, start = androidx.compose.ui.geometry.Offset(w*0.25f, h*0.5f), end = androidx.compose.ui.geometry.Offset(w*0.46f, h*0.71f))
        drawLine(color, strokeWidth = strokeW, start = androidx.compose.ui.geometry.Offset(w*0.46f, h*0.71f), end = androidx.compose.ui.geometry.Offset(w*0.76f, h*0.34f))
    }
}

fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / 1000) / 60
    return String.format("%02d:%02d", min, sec)
}

class MainActivityContent {
    companion object {
        @Composable
        fun GoldenPlayButton(
            isPlaying: Boolean,
            onClick: () -> Unit,
            modifier: Modifier = Modifier,
            size: Dp = 64.dp
        ) {
            val interactionSource = remember { MutableInteractionSource() }
            IconButton(
                onClick = onClick,
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                com.example.ui.theme.GoldGradientStart,
                                com.example.ui.theme.GoldGradientEnd
                            )
                        )
                    ),
                interactionSource = interactionSource
            ) {
                if (isPlaying) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 5.dp, height = 18.dp)
                                .background(Color.Black, shape = RoundedCornerShape(2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .size(width = 5.dp, height = 18.dp)
                                .background(Color.Black, shape = RoundedCornerShape(2.dp))
                        )
                    }
                } else {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val path = Path().apply {
                            moveTo(3.dp.toPx(), 0f)
                            lineTo(16.dp.toPx(), 10.dp.toPx())
                            lineTo(3.dp.toPx(), 16.dp.toPx())
                            close()
                        }
                        drawPath(path, Color.Black)
                    }
                }
            }
        }

        @Composable
        fun GoldenNavButton(
            isNext: Boolean,
            onClick: () -> Unit,
            modifier: Modifier = Modifier,
            size: Dp = 46.dp
        ) {
            IconButton(
                onClick = onClick,
                modifier = modifier
                    .size(size)
                    .border(1.5.dp, com.example.ui.theme.IslamicGoldAccent, CircleShape)
            ) {
                Canvas(modifier = Modifier.size(18.dp)) {
                    val w = this.size.width
                    val h = this.size.height
                    if (isNext) {
                        val p = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(w * 0.7f, h / 2)
                            lineTo(0f, h)
                            close()
                        }
                        drawPath(p, com.example.ui.theme.IslamicGoldAccent)
                        drawRect(
                            color = com.example.ui.theme.IslamicGoldAccent,
                            topLeft = androidx.compose.ui.geometry.Offset(w * 0.82f, 0f),
                            size = androidx.compose.ui.geometry.Size(w * 0.18f, h)
                        )
                    } else {
                        val p = Path().apply {
                            moveTo(w, 0f)
                            lineTo(w * 0.3f, h / 2)
                            lineTo(w, h)
                            close()
                        }
                        drawPath(p, com.example.ui.theme.IslamicGoldAccent)
                        drawRect(
                            color = com.example.ui.theme.IslamicGoldAccent,
                            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                            size = androidx.compose.ui.geometry.Size(w * 0.18f, h)
                        )
                    }
                }
            }
        }

        @Composable
        fun IslamicMandalaDisc(
            modifier: Modifier = Modifier,
            rotation: Float,
            isPlaying: Boolean
        ) {
            Box(
                modifier = modifier
                    .size(230.dp)
                    .shadow(16.dp, CircleShape)
                    .background(com.example.ui.theme.IslamicDarkSurface, CircleShape)
                    .border(2.5.dp, com.example.ui.theme.IslamicGoldAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().rotate(rotation)) {
                    val cx = this.size.width / 2
                    val cy = this.size.height / 2
                    val radius = this.size.width / 2
                    
                    drawCircle(com.example.ui.theme.IslamicGoldAccent.copy(alpha = 0.12f), radius = radius * 0.9f)
                    drawCircle(com.example.ui.theme.IslamicGoldAccent.copy(alpha = 0.25f), radius = radius * 0.74f, style = Stroke(width = 1.dp.toPx()))
                    drawCircle(com.example.ui.theme.IslamicGoldAccent.copy(alpha = 0.45f), radius = radius * 0.52f, style = Stroke(width = 1.5.dp.toPx()))
                    drawCircle(com.example.ui.theme.IslamicGoldAccent, radius = radius * 0.28f, style = Stroke(width = 2.dp.toPx()))
                    
                    for (i in 0 until 12) {
                        val angleRad = Math.toRadians((i * 30).toDouble())
                        val startX = (cx + Math.cos(angleRad) * (radius * 0.28f)).toFloat()
                        val startY = (cy + Math.sin(angleRad) * (radius * 0.28f)).toFloat()
                        val endX = (cx + Math.cos(angleRad) * (radius * 0.9f)).toFloat()
                        val endY = (cy + Math.sin(angleRad) * (radius * 0.9f)).toFloat()
                        
                        drawLine(
                            color = com.example.ui.theme.IslamicGoldAccent.copy(alpha = 0.2f),
                            start = androidx.compose.ui.geometry.Offset(startX, startY),
                            end = androidx.compose.ui.geometry.Offset(endX, endY),
                            strokeWidth = 1.dp.toPx()
                        )
                        
                        val dotX = (cx + Math.cos(angleRad) * (radius * 0.63f)).toFloat()
                        val dotY = (cy + Math.sin(angleRad) * (radius * 0.63f)).toFloat()
                        drawCircle(
                            color = com.example.ui.theme.IslamicGoldAccent,
                            radius = 3.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(dotX, dotY)
                        )
                        
                        val dotX2 = (cx + Math.cos(angleRad) * (radius * 0.82f)).toFloat()
                        val dotY2 = (cy + Math.sin(angleRad) * (radius * 0.82f)).toFloat()
                        drawCircle(
                            color = com.example.ui.theme.IslamicGoldAccent.copy(alpha = 0.45f),
                            radius = 2.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(dotX2, dotY2)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .size(85.dp)
                        .background(com.example.ui.theme.IslamicDarkBg, CircleShape)
                        .border(1.8.dp, com.example.ui.theme.IslamicGoldAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ياسر\nالدوسري",
                        color = com.example.ui.theme.IslamicGoldAccent,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ImmersiveBottomNavigation(
    activeFilter: SurahFilter,
    onSelectFilter: (SurahFilter) -> Unit
) {
    Surface(
        color = com.example.ui.theme.IslamicDarkSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Default.List,
                label = "القرآن",
                isActive = activeFilter == SurahFilter.ALL,
                onClick = { onSelectFilter(SurahFilter.ALL) }
            )
            BottomNavItem(
                icon = Icons.Default.CheckCircle,
                label = "المحملات",
                isActive = activeFilter == SurahFilter.DOWNLOADED,
                onClick = { onSelectFilter(SurahFilter.DOWNLOADED) }
            )
            BottomNavItem(
                icon = Icons.Default.Favorite,
                label = "المفضلة",
                isActive = activeFilter == SurahFilter.FAVORITES,
                onClick = { onSelectFilter(SurahFilter.FAVORITES) }
            )
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "مدنية",
                isActive = activeFilter == SurahFilter.MEDINAN,
                onClick = { onSelectFilter(SurahFilter.MEDINAN) }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.SubText,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            color = if (isActive) com.example.ui.theme.IslamicGoldAccent else com.example.ui.theme.SubText,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}
