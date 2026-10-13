package com.mimusica.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val Bg = Color(0xFF030303)
private val Surface1 = Color(0xFF212121)
private val Accent = Color(0xFFFF0000)
private val Muted = Color(0xFFAAAAAA)

@Composable
fun MiMusicaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Bg, surface = Bg, primary = Accent, onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Art(song: Song?, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp)).background(Surface1),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.MusicNote, null, tint = Muted)
        if (song != null) {
            AsyncImage(
                model = song.artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun MusicApp(vm: PlayerViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Pair<String, List<Song>>?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    BackHandler(enabled = showPlayer || detail != null) {
        if (showPlayer) showPlayer = false else detail = null
    }

    val albums = remember(vm.songs) { vm.songs.groupBy { it.albumId }.values.toList() }
    val artists = remember(vm.songs) { vm.songs.groupBy { it.artist }.toList().sortedBy { it.first } }

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize()) {
            val d = detail
            if (d != null) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { detail = null }) { Icon(Icons.Default.ArrowBack, "Volver") }
                    Text(d.first, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                SongList(d.second, vm) { vm.play(d.second, it) }
            } else {
                Text(
                    "Mi Música",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
                TabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = Color.White) {
                    listOf("Canciones", "Álbumes", "Artistas").forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                    }
                }
                if (vm.songs.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No se encontraron canciones", color = Muted)
                    }
                } else when (tab) {
                    0 -> Column(Modifier.weight(1f)) {
                        Row(
                            Modifier.padding(16.dp).clip(RoundedCornerShape(50)).background(Color.White)
                                .clickable { vm.shufflePlay(vm.songs) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shuffle, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Reproducir aleatorio", color = Color.Black, fontWeight = FontWeight.Medium)
                        }
                        SongList(vm.songs, vm) { vm.play(vm.songs, it) }
                    }
                    1 -> LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(albums) { list ->
                            Column(Modifier.clickable { detail = list.first().album to list }) {
                                Art(list.first(), Modifier.fillMaxWidth().aspectRatio(1f))
                                Text(list.first().album, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                Text(list.first().artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    else -> LazyColumn(Modifier.weight(1f)) {
                        items(artists) { (name, list) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { detail = name to list }.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Art(list.first(), Modifier.size(52.dp).clip(RoundedCornerShape(26.dp)))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${list.size} canciones", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            if (vm.current != null) MiniPlayer(vm) { showPlayer = true }
        }

        if (showPlayer && vm.current != null) FullPlayer(vm) { showPlayer = false }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SongList(
    songs: List<Song>, vm: PlayerViewModel, onClick: (Int) -> Unit
) {
    LazyColumn(Modifier.weight(1f)) {
        items(songs.size, key = { songs[it].id }) { i ->
            val s = songs[i]
            val active = vm.current?.id == s.id
            Row(
                Modifier.fillMaxWidth().clickable { onClick(i) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Art(s, Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (active) Accent else Color.White)
                    Text(s.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(fmt(s.durationMs), color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val s = vm.current ?: return
    Column(Modifier.fillMaxWidth().background(Surface1).clickable { onOpen() }) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(s, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(s.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { vm.togglePlay() }) {
                Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir/Pausar")
            }
            IconButton(onClick = { vm.next() }) { Icon(Icons.Default.SkipNext, "Siguiente") }
        }
        LinearProgressIndicator(
            progress = { if (vm.durationMs > 0) vm.positionMs.toFloat() / vm.durationMs else 0f },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Color.White,
            trackColor = Color(0xFF444444)
        )
    }
}

@Composable
private fun FullPlayer(vm: PlayerViewModel, onClose: () -> Unit) {
    val s = vm.current ?: return
    var dragging by remember { mutableStateOf<Float?>(null) }
    val max = vm.durationMs.coerceAtLeast(1L).toFloat()

    Column(
        Modifier.fillMaxSize().background(Bg).clickable(enabled = false) {}.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Cerrar", modifier = Modifier.size(32.dp)) }
        }
        Spacer(Modifier.height(16.dp))
        Art(s, Modifier.fillMaxWidth().aspectRatio(1f))
        Spacer(Modifier.height(28.dp))
        Text(s.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
        Text(s.artist, color = Muted, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Slider(
            value = dragging ?: vm.positionMs.toFloat().coerceIn(0f, max),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { vm.seekTo(it.toLong()) }; dragging = null },
            valueRange = 0f..max,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmt((dragging ?: vm.positionMs.toFloat()).toLong()), color = Muted, fontSize = 12.sp)
            Text(fmt(vm.durationMs), color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, "Aleatorio", tint = if (vm.shuffle) Accent else Muted)
            }
            IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipPrevious, "Anterior", modifier = Modifier.size(36.dp))
            }
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(36.dp)).background(Color.White).clickable { vm.togglePlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "Reproducir/Pausar", tint = Color.Black, modifier = Modifier.size(40.dp)
                )
            }
            IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.SkipNext, "Siguiente", modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.size(48.dp))
        }
    }
}
