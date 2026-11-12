package com.mimusica.player

import android.app.Application
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.SubcomposeAsyncImage
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale

data class Song(
    val id: Long,
    val title: String,
    val displayName: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val artUri: Uri,
    val folder: String,
    val folderPath: String,
    val filePath: String,
    val dateAdded: Long
)

data class FolderInfo(
    val name: String,
    val path: String,
    val count: Int
)

val DarkBg = Color(0xFF030303)
val SurfaceDark = Color(0xFF121212)
val SurfaceCard = Color(0xFF1C1C1E)
val AccentRed = Color(0xFFE8112D)

private val MiMusicaColorScheme = darkColorScheme(
    primary = AccentRed,
    onPrimary = Color.White,
    background = DarkBg,
    onBackground = Color.White,
    surface = SurfaceDark,
    onSurface = Color.White,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = Color(0xFFB3B3B3)
)

@Composable
fun MiMusicaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MiMusicaColorScheme, content = content)
}

@Composable
fun WithIntro(content: @Composable () -> Unit) {
    var showIntro by remember { mutableStateOf(true) }
    var startAnim by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0.8f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "introScale"
    )

    LaunchedEffect(Unit) {
        startAnim = true
        delay(750)
        showIntro = false
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        content()
        AnimatedVisibility(
            visible = showIntro,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(DarkBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(AccentRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Mi Música", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("mimusica_filters", Context.MODE_PRIVATE)
    private var rawSongs: List<Song> = emptyList()

    var allSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var availableFolders by mutableStateOf<List<FolderInfo>>(emptyList())
        private set
    var quickPicks by mutableStateOf<List<Song>>(emptyList())
        private set
    var currentSong by mutableStateOf<Song?>(null)
        private set
    var activeQueue by mutableStateOf<List<Song>>(emptyList())
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var currentPosition by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var shuffleEnabled by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_ALL)
        private set
    var isScanning by mutableStateOf(false)
        private set

    var hideWhatsAppAndApps by mutableStateOf(prefs.getBoolean("hide_whatsapp", true))
        private set
    var hideVideoAndSystemAudio by mutableStateOf(prefs.getBoolean("hide_video_audio", true))
        private set
    var minDurationSeconds by mutableIntStateOf(prefs.getInt("min_duration_sec", 35))
        private set
    var excludedFolders by mutableStateOf<Set<String>>(
        prefs.getStringSet("excluded_folders", emptySet())?.toSet() ?: emptySet()
    )
        private set

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    init {
        connectService()
        viewModelScope.launch {
            while (true) {
                controller?.let { player ->
                    if (player.isPlaying) {
                        currentPosition = player.currentPosition.coerceAtLeast(0L)
                        if (player.duration > 0) duration = player.duration
                    }
                }
                delay(300)
            }
        }
    }

    private fun connectService() {
        val context = getApplication<Application>()
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync().also { future ->
            future.addListener({
                try {
                    val mc = future.get()
                    controller = mc
                    mc.repeatMode = repeatMode
                    mc.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            val id = mediaItem?.mediaId?.toLongOrNull()
                            if (id != null) {
                                currentSong = allSongs.find { it.id == id }
                                currentPosition = 0L
                                duration = currentSong?.duration ?: 0L
                            }
                        }
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY && mc.duration > 0) duration = mc.duration
                        }
                        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                            shuffleEnabled = shuffleModeEnabled
                        }
                        override fun onRepeatModeChanged(newRepeatMode: Int) { repeatMode = newRepeatMode }
                    })
                } catch (_: Exception) {}
            }, MoreExecutors.directExecutor())
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            isScanning = true
            val scanned = withContext(Dispatchers.IO) { queryAllAudioFiles() }
            rawSongs = scanned
            availableFolders = scanned
                .groupBy { it.folderPath }
                .map { (path, songs) ->
                    FolderInfo(
                        name = songs.first().folder,
                        path = path,
                        count = songs.size
                    )
                }
                .sortedByDescending { it.count }
            applyFilters(forceRefreshPicks = quickPicks.isEmpty())
            isScanning = false
        }
    }

    fun updateHideWhatsApp(hide: Boolean) {
        hideWhatsAppAndApps = hide
        prefs.edit().putBoolean("hide_whatsapp", hide).apply()
        applyFilters(forceRefreshPicks = true)
    }

    fun updateHideVideoAudio(hide: Boolean) {
        hideVideoAndSystemAudio = hide
        prefs.edit().putBoolean("hide_video_audio", hide).apply()
        applyFilters(forceRefreshPicks = true)
    }

    fun updateMinDuration(seconds: Int) {
        minDurationSeconds = seconds
        prefs.edit().putInt("min_duration_sec", seconds).apply()
        applyFilters(forceRefreshPicks = true)
    }

    fun toggleFolderExcluded(folderPath: String) {
        val updated = if (excludedFolders.contains(folderPath)) {
            excludedFolders - folderPath
        } else {
            excludedFolders + folderPath
        }
        excludedFolders = updated
        prefs.edit().putStringSet("excluded_folders", updated).apply()
        applyFilters(forceRefreshPicks = true)
    }

    fun setOnlyMusicFolders() {
        val nonMusic = availableFolders
            .filter { f ->
                val lower = (f.path + "/" + f.name).lowercase()
                lower.contains("whatsapp") ||
                    lower.contains("telegram") ||
                    lower.contains("record") ||
                    lower.contains("dcim") ||
                    lower.contains("camera") ||
                    lower.contains("movies") ||
                    lower.contains("miui") ||
                    lower.contains("android/media") ||
                    lower.contains("android/data")
            }
            .map { it.path }
            .toSet()
        excludedFolders = nonMusic
        prefs.edit().putStringSet("excluded_folders", nonMusic).apply()
        applyFilters(forceRefreshPicks = true)
    }

    fun includeAllFolders() {
        excludedFolders = emptySet()
        prefs.edit().putStringSet("excluded_folders", emptySet()).apply()
        applyFilters(forceRefreshPicks = true)
    }

    private fun applyFilters(forceRefreshPicks: Boolean = false) {
        val minMs = minDurationSeconds * 1000L
        val waRegex = Regex(".*-WA\\d+.*", RegexOption.IGNORE_CASE)

        val filtered = rawSongs.filter { song ->
            if (song.duration < minMs) return@filter false
            if (excludedFolders.contains(song.folderPath)) return@filter false

            val lowerPath = song.filePath.lowercase()
            val lowerFolder = song.folder.lowercase()
            val lowerTitle = song.title.lowercase()
            val lowerName = song.displayName.lowercase()

            if (hideWhatsAppAndApps) {
                val isWhatsAppOrAppAudio =
                    waRegex.matches(song.title) ||
                    waRegex.matches(song.displayName) ||
                    lowerTitle.contains("-wa0") ||
                    lowerTitle.contains("-wa1") ||
                    lowerTitle.contains("-wa2") ||
                    lowerName.contains("-wa0") ||
                    lowerName.contains("-wa1") ||
                    lowerName.contains("-wa2") ||
                    lowerTitle.startsWith("aud-") ||
                    lowerTitle.startsWith("ptt-") ||
                    lowerName.startsWith("aud-") ||
                    lowerName.startsWith("ptt-") ||
                    lowerName.endsWith(".opus") ||
                    lowerFolder.contains("whatsapp") ||
                    lowerFolder.contains("telegram") ||
                    lowerFolder.contains("voice") ||
                    lowerFolder.contains("record") ||
                    lowerFolder.contains("messenger") ||
                    lowerFolder.contains("discord") ||
                    lowerPath.contains("whatsapp") ||
                    lowerPath.contains("telegram") ||
                    lowerPath.contains("voice notes") ||
                    lowerPath.contains("/recordings") ||
                    lowerPath.contains("callrecord") ||
                    lowerPath.contains("sound_recorder") ||
                    lowerPath.contains("/android/media/")
                if (isWhatsAppOrAppAudio) return@filter false
            }

            if (hideVideoAndSystemAudio) {
                val isVideoOrSystem =
                    lowerFolder.contains("camera") ||
                    lowerFolder.contains("movies") ||
                    lowerFolder.contains("dcim") ||
                    lowerFolder.contains("screenrecorder") ||
                    lowerFolder.contains("notifications") ||
                    lowerFolder.contains("ringtones") ||
                    lowerFolder.contains("alarms") ||
                    lowerPath.contains("/dcim/") ||
                    lowerPath.contains("/camera/") ||
                    lowerPath.contains("/movies/") ||
                    lowerPath.contains("/screenrecorder") ||
                    lowerPath.contains("/.thumbnails") ||
                    lowerPath.contains("/android/data/") ||
                    lowerPath.contains("/notifications") ||
                    lowerPath.contains("/ringtones") ||
                    lowerPath.contains("/alarms")
                if (isVideoOrSystem) return@filter false
            }

            true
        }

        allSongs = filtered
        if (forceRefreshPicks || quickPicks.none { q -> filtered.any { it.id == q.id } }) {
            refreshQuickPicks()
        }
    }

    fun refreshQuickPicks() {
        quickPicks = if (allSongs.size <= 12) allSongs else allSongs.shuffled().take(12)
    }

    private fun queryAllAudioFiles(): List<Song> {
        val context = getApplication<Application>()
        val list = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val displayCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val dateCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val artBaseUri = Uri.parse("content://media/external/audio/albumart")

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val dur = cursor.getLong(durationCol)
                    if (dur < 5000L) continue

                    val path = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val displayName = if (displayCol >= 0) cursor.getString(displayCol)?.trim().orEmpty() else ""
                    val parentFile = if (path.isNotEmpty()) File(path).parentFile else null
                    val folderName = parentFile?.name ?: "Música"
                    val folderPath = parentFile?.absolutePath ?: folderName

                    val rawTitle = cursor.getString(titleCol)?.trim().orEmpty()
                    val title = if (rawTitle.isNotEmpty() && !rawTitle.equals("<unknown>", true)) {
                        rawTitle
                    } else {
                        File(path).nameWithoutExtension.ifEmpty {
                            displayName.substringBeforeLast(".").ifEmpty { "Pista $id" }
                        }
                    }

                    val rawArtist = cursor.getString(artistCol)?.trim().orEmpty()
                    val artist = if (rawArtist.isNotEmpty() && !rawArtist.equals("<unknown>", true)) rawArtist
                    else "Artista desconocido"

                    val album = cursor.getString(albumCol)?.trim().orEmpty().ifEmpty { "Sin álbum" }
                    val albumId = cursor.getLong(albumIdCol)
                    val dateAdded = if (dateCol >= 0) cursor.getLong(dateCol) else 0L

                    list.add(
                        Song(
                            id = id,
                            title = title,
                            displayName = displayName,
                            artist = artist,
                            album = album,
                            duration = dur,
                            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                            artUri = ContentUris.withAppendedId(artBaseUri, albumId),
                            folder = folderName,
                            folderPath = folderPath,
                            filePath = path,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun playSongWithFullQueue(song: Song, queueSource: List<Song> = allSongs, shuffle: Boolean = false) {
        val player = controller ?: return
        val listToPlay = if (queueSource.isNotEmpty()) queueSource else allSongs
        if (listToPlay.isEmpty()) return

        activeQueue = listToPlay
        val startIndex = listToPlay.indexOfFirst { it.id == song.id }.coerceAtLeast(0)

        val mediaItems = listToPlay.map { item ->
            MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtist(item.artist)
                        .setAlbumTitle(item.album)
                        .setArtworkUri(item.artUri)
                        .build()
                )
                .build()
        }

        player.shuffleModeEnabled = shuffle
        shuffleEnabled = shuffle
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
        currentSong = listToPlay[startIndex]
        duration = currentSong?.duration ?: 0L
        currentPosition = 0L
    }

    fun playAllShuffled(list: List<Song> = allSongs) {
        if (list.isEmpty()) return
        playSongWithFullQueue(song = list.random(), queueSource = list, shuffle = true)
    }

    fun togglePlayPause() {
        val player = controller ?: return
        if (player.isPlaying) player.pause()
        else if (currentSong == null && allSongs.isNotEmpty()) playSongWithFullQueue(allSongs.first())
        else player.play()
    }

    fun skipNext() {
        controller?.let { if (it.hasNextMediaItem()) { it.seekToNextMediaItem(); it.play() } }
    }

    fun skipPrevious() {
        controller?.let {
            if (it.currentPosition > 3500L) it.seekTo(0L)
            else if (it.hasPreviousMediaItem()) { it.seekToPreviousMediaItem(); it.play() }
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        currentPosition = positionMs
    }

    fun toggleShuffle() {
        controller?.let {
            val next = !it.shuffleModeEnabled
            it.shuffleModeEnabled = next
            shuffleEnabled = next
        }
    }

    fun cycleRepeatMode() {
        controller?.let {
            val next = when (it.repeatMode) {
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_ALL
            }
            it.repeatMode = next
            repeatMode = next
        }
    }

    override fun onCleared() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}

@Composable
fun MusicApp(vm: PlayerViewModel, onDeleteSong: (Song) -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolder by remember { mutableStateOf("Todas") }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    BackHandler(enabled = showFullPlayer) { showFullPlayer = false }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Scaffold(
            containerColor = DarkBg,
            topBar = {
                TopHeaderBar(
                    totalCount = vm.allSongs.size,
                    isScanning = vm.isScanning,
                    searchQuery = searchQuery,
                    onSearchChange = {
                        searchQuery = it
                        if (it.isNotEmpty() && selectedTab != 1) selectedTab = 1
                    },
                    onRefresh = { vm.loadSongs() },
                    onOpenSettings = { showSettingsSheet = true }
                )
            },
            bottomBar = {
                Column(modifier = Modifier.background(SurfaceDark)) {
                    vm.currentSong?.let { song ->
                        MiniPlayerBar(
                            song = song,
                            isPlaying = vm.isPlaying,
                            progress = if (vm.duration > 0) (vm.currentPosition.toFloat() / vm.duration.toFloat()).coerceIn(0f, 1f) else 0f,
                            onTogglePlay = { vm.togglePlayPause() },
                            onNext = { vm.skipNext() },
                            onOpenPlayer = { showFullPlayer = true }
                        )
                    }
                    NavigationBar(containerColor = DarkBg, tonalElevation = 0.dp) {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Icon(Icons.Rounded.Home, contentDescription = "Inicio") },
                            label = { Text("Inicio") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = AccentRed.copy(alpha = 0.25f),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = "Biblioteca") },
                            label = { Text("Biblioteca (${vm.allSongs.size})") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                indicatorColor = AccentRed.copy(alpha = 0.25f),
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            if (vm.isScanning && vm.allSongs.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AccentRed)
                        Spacer(Modifier.height(12.dp))
                        Text("Buscando toda tu música...", color = Color.LightGray)
                    }
                }
            } else if (vm.allSongs.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(innerPadding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No hay canciones con los filtros actuales.", color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showSettingsSheet = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                            ) {
                                Text("Abrir Ajustes")
                            }
                            OutlinedButton(onClick = { vm.loadSongs() }) {
                                Text("Re-escanear", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                when (selectedTab) {
                    0 -> HomeScreen(
                        vm = vm,
                        padding = innerPadding,
                        onOpenLibraryWithFolder = { folder ->
                            selectedFolder = folder
                            selectedTab = 1
                        },
                        onDeleteSong = onDeleteSong
                    )
                    1 -> LibraryScreen(
                        vm = vm,
                        padding = innerPadding,
                        searchQuery = searchQuery,
                        selectedFolder = selectedFolder,
                        onFolderSelect = { selectedFolder = it },
                        onDeleteSong = onDeleteSong
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showFullPlayer && vm.currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            vm.currentSong?.let { song ->
                FullPlayerScreen(
                    vm = vm,
                    song = song,
                    onCollapse = { showFullPlayer = false },
                    onDeleteSong = { showFullPlayer = false; onDeleteSong(it) }
                )
            }
        }

        if (showSettingsSheet) {
            FilterSettingsSheet(
                vm = vm,
                onDismiss = { showSettingsSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSettingsSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Ajustes de biblioteca",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mostrando ${vm.allSongs.size} canciones",
                            color = AccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    TextButton(onClick = { vm.setOnlyMusicFolders() }) {
                        Text("Solo música", color = AccentRed)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCard)
                        .clickable { vm.updateHideWhatsApp(!vm.hideWhatsAppAndApps) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ocultar audios de WhatsApp y apps", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Bloquea todos los AUD-...-WA, notas de voz, Telegram y grabaciones", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = vm.hideWhatsAppAndApps,
                        onCheckedChange = { vm.updateHideWhatsApp(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentRed)
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCard)
                        .clickable { vm.updateHideVideoAudio(!vm.hideVideoAndSystemAudio) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ocultar pistas de videos y tonos", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Excluye carpetas Camera, Movies, DCIM y tonos del sistema", color = Color.Gray, fontSize = 12.sp)
                    }
                    Switch(
                        checked = vm.hideVideoAndSystemAudio,
                        onCheckedChange = { vm.updateHideVideoAudio(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AccentRed)
                    )
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceCard)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Duración mínima de pista", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text(
                            text = if (vm.minDurationSeconds == 0) "Sin límite" else "${vm.minDurationSeconds} seg",
                            color = AccentRed,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Slider(
                        value = vm.minDurationSeconds.toFloat(),
                        onValueChange = { vm.updateMinDuration(it.toInt()) },
                        valueRange = 0f..120f,
                        steps = 23,
                        colors = SliderDefaults.colors(
                            thumbColor = AccentRed,
                            activeTrackColor = AccentRed,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                    Text("Sube a 45-60 seg si aún se cuelan audios cortos.", color = Color.Gray, fontSize = 11.sp)
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Carpetas incluidas (${vm.availableFolders.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (vm.excludedFolders.isNotEmpty()) {
                        TextButton(onClick = { vm.includeAllFolders() }) {
                            Text("Marcar todas", color = Color.LightGray, fontSize = 12.sp)
                        }
                    }
                }
            }

            items(vm.availableFolders, key = { it.path }) { folder ->
                val isIncluded = !vm.excludedFolders.contains(folder.path)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceCard)
                        .clickable { vm.toggleFolderExcluded(folder.path) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isIncluded,
                        onCheckedChange = { vm.toggleFolderExcluded(folder.path) },
                        colors = CheckboxDefaults.colors(checkedColor = AccentRed, checkmarkColor = Color.White)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            color = if (isIncluded) Color.White else Color.Gray,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = folder.path.removePrefix("/storage/emulated/0/"),
                            color = Color.Gray,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${folder.count}",
                        color = if (isIncluded) AccentRed else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun TopHeaderBar(
    totalCount: Int,
    isScanning: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(AccentRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Mi Música", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("$totalCount canciones", color = Color(0xFF9E9E9E), fontSize = 11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRefresh, enabled = !isScanning) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar", tint = Color.White)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Ajustes y filtros", tint = Color.White)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Buscar canción, artista o carpeta...", fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.Gray) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Limpiar", tint = Color.Gray)
                    }
                }
            },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceCard,
                unfocusedContainerColor = SurfaceCard,
                focusedBorderColor = AccentRed,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
    }
}

@Composable
private fun HomeScreen(
    vm: PlayerViewModel,
    padding: PaddingValues,
    onOpenLibraryWithFolder: (String) -> Unit,
    onDeleteSong: (Song) -> Unit
) {
    var homeMoodFilter by remember { mutableStateOf("Todo") }
    val moods = listOf("Todo", "Recientes", "Largas (+4m)", "Cortas")

    val displayedRecent = remember(vm.allSongs, homeMoodFilter) {
        when (homeMoodFilter) {
            "Recientes" -> vm.allSongs.sortedByDescending { it.dateAdded }.take(30)
            "Largas (+4m)" -> vm.allSongs.filter { it.duration >= 240_000L }.take(30)
            "Cortas" -> vm.allSongs.filter { it.duration < 180_000L }.take(30)
            else -> vm.allSongs.sortedByDescending { it.dateAdded }.take(25)
        }
    }

    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Buenos días"
            in 12..18 -> "Buenas tardes"
            else -> "Buenas noches"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(moods) { mood ->
                    FilterChip(
                        selected = homeMoodFilter == mood,
                        onClick = { homeMoodFilter = mood },
                        label = { Text(mood) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.White,
                            selectedLabelColor = DarkBg,
                            containerColor = SurfaceCard,
                            labelColor = Color.White
                        )
                    )
                }
            }
        }

        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(greeting, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { vm.playAllShuffled(vm.allSongs) },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mezclar todo", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { vm.allSongs.firstOrNull()?.let { vm.playSongWithFullQueue(it, vm.allSongs, false) } },
                        modifier = Modifier.height(46.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("En orden")
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("SELECCIÓN RÁPIDA", color = Color(0xFF9E9E9E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("Inicia radio con toda tu música", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { vm.refreshQuickPicks() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cambiar", color = AccentRed)
                }
            }

            Spacer(Modifier.height(8.dp))

            val chunkedPicks = remember(vm.quickPicks) { vm.quickPicks.chunked(3) }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(chunkedPicks) { columnSongs ->
                    Column(modifier = Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        columnSongs.forEach { song ->
                            QuickPickTile(
                                song = song,
                                isCurrent = vm.currentSong?.id == song.id,
                                onClick = { vm.playSongWithFullQueue(song, vm.allSongs, shuffle = false) }
                            )
                        }
                    }
                }
            }
        }

        val topFolders = vm.allSongs.groupBy { it.folder }.filter { it.value.size >= 2 }.toList().take(6)
        if (topFolders.isNotEmpty()) {
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Tus mixes por carpeta",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(topFolders) { (folderName, songsInFolder) ->
                        Column(
                            modifier = Modifier
                                .width(140.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceCard)
                                .clickable {
                                    vm.playAllShuffled(songsInFolder)
                                }
                                .padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF281014)),
                                contentAlignment = Alignment.Center
                            ) {
                                SubcomposeAsyncImage(
                                    model = songsInFolder.first().artUri,
                                    contentDescription = folderName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    error = {
                                        Icon(
                                            Icons.Rounded.Album,
                                            contentDescription = null,
                                            tint = AccentRed,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Mix $folderName",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${songsInFolder.size} canciones",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (homeMoodFilter == "Todo") "Añadidas recientemente" else homeMoodFilter,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { onOpenLibraryWithFolder("Todas") }) {
                    Text("Ver todas", color = AccentRed)
                }
            }
        }

        items(displayedRecent, key = { "recent_${it.id}" }) { song ->
            SongRowItem(
                song = song,
                isCurrent = vm.currentSong?.id == song.id,
                isPlaying = vm.isPlaying && vm.currentSong?.id == song.id,
                onClick = { vm.playSongWithFullQueue(song, vm.allSongs, shuffle = false) },
                onDelete = { onDeleteSong(song) }
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    vm: PlayerViewModel,
    padding: PaddingValues,
    searchQuery: String,
    selectedFolder: String,
    onFolderSelect: (String) -> Unit,
    onDeleteSong: (Song) -> Unit
) {
    val folders = remember(vm.allSongs) { listOf("Todas") + vm.allSongs.map { it.folder }.distinct().sorted() }
    val filteredSongs = remember(vm.allSongs, searchQuery, selectedFolder) {
        vm.allSongs.filter { song ->
            val matchesFolder = selectedFolder == "Todas" || song.folder == selectedFolder
            val matchesQuery = searchQuery.isBlank() ||
                song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true) ||
                song.album.contains(searchQuery, ignoreCase = true) ||
                song.folder.contains(searchQuery, ignoreCase = true)
            matchesFolder && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folders) { folder ->
                    FilterChip(
                        selected = selectedFolder == folder,
                        onClick = { onFolderSelect(folder) },
                        label = { Text(folder) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentRed,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceCard,
                            labelColor = Color.LightGray
                        )
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${filteredSongs.size} canciones", color = Color.LightGray, fontSize = 13.sp)
                TextButton(onClick = { vm.playAllShuffled(filteredSongs) }) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Aleatorio aquí", color = AccentRed)
                }
            }
        }

        items(filteredSongs, key = { "lib_${it.id}" }) { song ->
            SongRowItem(
                song = song,
                isCurrent = vm.currentSong?.id == song.id,
                isPlaying = vm.isPlaying && vm.currentSong?.id == song.id,
                onClick = { vm.playSongWithFullQueue(song, filteredSongs, shuffle = false) },
                onDelete = { onDeleteSong(song) }
            )
        }
    }
}

@Composable
private fun QuickPickTile(song: Song, isCurrent: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrent) AccentRed.copy(alpha = 0.22f) else SurfaceCard)
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtThumb(artUri = song.artUri, size = 48.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) AccentRed else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} • ${formatTime(song.duration)}",
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isCurrent) Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = AccentRed)
    }
}

@Composable
private fun SongRowItem(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(if (isCurrent) AccentRed.copy(alpha = 0.14f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtThumb(artUri = song.artUri, size = 50.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isCurrent) AccentRed else Color.White,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${song.artist} • ${song.folder} • ${formatTime(song.duration)}",
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isPlaying) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = AccentRed, modifier = Modifier.padding(end = 4.dp))
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones", tint = Color.Gray)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.background(SurfaceCard)
            ) {
                DropdownMenuItem(
                    text = { Text("Eliminar del teléfono", color = Color(0xFFFF6B6B)) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFFF6B6B)) },
                    onClick = { menuExpanded = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun MiniPlayerBar(
    song: Song,
    isPlaying: Boolean,
    progress: Float,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(SurfaceCard).clickable { onOpenPlayer() }) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.5.dp),
            color = AccentRed,
            trackColor = Color.DarkGray
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtThumb(artUri = song.artUri, size = 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Play/Pausa",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Siguiente", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullPlayerScreen(
    vm: PlayerViewModel,
    song: Song,
    onCollapse: () -> Unit,
    onDeleteSong: (Song) -> Unit
) {
    var showQueueSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF2B090E), DarkBg, DarkBg)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimizar", tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("REPRODUCIENDO DESDE", color = Color.LightGray, fontSize = 10.sp)
                Text(song.folder, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            IconButton(onClick = { onDeleteSong(song) }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Eliminar", tint = Color.LightGray)
            }
        }

        Spacer(Modifier.weight(0.5f))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = song.artUri,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                error = {
                    Box(Modifier.fillMaxSize().background(SurfaceCard), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Album, contentDescription = null, tint = AccentRed.copy(alpha = 0.8f), modifier = Modifier.size(110.dp))
                    }
                }
            )
        }

        Spacer(Modifier.weight(0.5f))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(song.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("${song.artist} • ${song.album}", color = Color(0xFFB3B3B3), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(16.dp))

        val maxDur = vm.duration.coerceAtLeast(1L)
        Slider(
            value = vm.currentPosition.coerceIn(0L, maxDur).toFloat(),
            onValueChange = { vm.seekTo(it.toLong()) },
            valueRange = 0f..maxDur.toFloat(),
            colors = SliderDefaults.colors(thumbColor = AccentRed, activeTrackColor = AccentRed, inactiveTrackColor = Color.DarkGray)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(vm.currentPosition), color = Color.Gray, fontSize = 12.sp)
            Text(formatTime(maxDur), color = Color.Gray, fontSize = 12.sp)
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { vm.toggleShuffle() }) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Aleatorio", tint = if (vm.shuffleEnabled) AccentRed else Color.Gray, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = { vm.skipPrevious() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            Surface(onClick = { vm.togglePlayPause() }, shape = CircleShape, color = AccentRed, modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pausa",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            IconButton(onClick = { vm.skipNext() }, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Siguiente", tint = Color.White, modifier = Modifier.size(40.dp))
            }
            IconButton(onClick = { vm.cycleRepeatMode() }) {
                val icon = if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                val tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) Color.Gray else AccentRed
                Icon(icon, contentDescription = "Repetir", tint = tint, modifier = Modifier.size(26.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { showQueueSheet = true }) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Color.LightGray)
            Spacer(Modifier.width(8.dp))
            Text("Cola automática (${vm.activeQueue.size} canciones)", color = Color.LightGray)
        }
    }

    if (showQueueSheet) {
        ModalBottomSheet(onDismissRequest = { showQueueSheet = false }, containerColor = SurfaceDark) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(
                    text = "Cola de reproducción (${vm.activeQueue.size})",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyColumn(modifier = Modifier.height(380.dp)) {
                    items(vm.activeQueue, key = { "q_${it.id}" }) { item ->
                        SongRowItem(
                            song = item,
                            isCurrent = vm.currentSong?.id == item.id,
                            isPlaying = vm.isPlaying && vm.currentSong?.id == item.id,
                            onClick = {
                                vm.playSongWithFullQueue(item, vm.activeQueue, shuffle = vm.shuffleEnabled)
                                showQueueSheet = false
                            },
                            onDelete = { onDeleteSong(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArtThumb(artUri: Uri, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)).background(Color(0xFF262629)),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = artUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            error = {
                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = AccentRed, modifier = Modifier.size(size * 0.5f))
            }
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
