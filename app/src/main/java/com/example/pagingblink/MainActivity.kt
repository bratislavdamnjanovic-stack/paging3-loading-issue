package com.example.pagingblink

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.room.Room
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

private const val TAG = "BLINK"
private const val PAGE_SIZE = 20

/**
 * Reproduction of a media-grid "blink".
 *
 * A 3-column [LazyVerticalGrid] of square GlideImage tiles backed by a Room
 * [androidx.paging.PagingSource] with a [MediaRemoteMediator] that inserts appended
 * pages into Room. Placeholders are ENABLED (enablePlaceholders is not set, so it
 * defaults to true).
 *
 * Every APPEND writes to Room, which invalidates the Room PagingSource and re-emits fresh
 * PagingData. With CONTENT keys the re-emission flips a visible tile's key whenever peek()
 * transiently returns null (placeholder), disposing the tile composition and resetting
 * GlideImage's loaded state -> blink. With INDEX keys the key is position-stable, so the
 * composition (and GlideImage state) survives -> no blink.
 *
 * Toggle the switch at the top and scroll. Watch `adb logcat -s BLINK` for DISPOSE/compose
 * pairs on tiles that stay on screen.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ReproScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalPagingApi::class)
@Composable
fun ReproScreen() {
    val context = LocalContext.current

    // false = content key (reproduces the blink), true = index key (no blink)
    var useIndexKey by remember { mutableStateOf(false) }

    val db = remember {
        Room.databaseBuilder(context, AppDatabase::class.java, "media.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val pager = remember {
        Pager(
            // Placeholders stay enabled (default true) so the list reserves slots for
            // not-yet-loaded items and does not jump/resize as pages arrive.
            config = PagingConfig(
                pageSize = PAGE_SIZE,
                initialLoadSize = PAGE_SIZE * 2,
            ),
            remoteMediator = MediaRemoteMediator(db),
            pagingSourceFactory = { db.mediaDao().observeAll() },
        )
    }
    val mediaItems = pager.flow.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(checked = useIndexKey, onCheckedChange = { useIndexKey = it })
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (useIndexKey) {
                    "INDEX key  (no blink, shifts on delete)"
                } else {
                    "CONTENT key  (grid tiles blink while scrolling)"
                },
                style = MaterialTheme.typography.titleSmall,
            )
        }
        HorizontalDivider()

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                count = mediaItems.itemCount,
                key = if (useIndexKey) {
                    { index -> index }
                } else {
                    mediaItems.itemKey { "${it.id}_${it.messageId}" }
                },
                contentType = mediaItems.itemContentType { "MediaPreview" },
            ) { index ->
                val rawItem = mediaItems[index]

                // cachedItem guard: remember the last non-null value so the tile
                // does not flash to a gray box on transient placeholder nulls.
                val cachedItem = remember { mutableStateOf(rawItem) }
                if (rawItem != null) {
                    cachedItem.value = rawItem
                }

                MediaGridItem(item = cachedItem.value)
            }
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun MediaGridItem(item: MediaEntity?) {
    // Logs prove whether a tile's composition is disposed/recreated during scroll.
    DisposableEffect(Unit) {
        Log.d(TAG, "compose  tile id=${item?.id}")
        onDispose { Log.d(TAG, "DISPOSE  tile id=${item?.id}") }
    }

    val tileModifier = Modifier
        .aspectRatio(1f)
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFFE0E0E0))

    if (item == null) {
        Box(modifier = tileModifier)
    } else {
        GlideImage(
            model = item.imageUrl,
            contentDescription = "Media thumbnail",
            contentScale = ContentScale.Crop,
            modifier = tileModifier,
        )
    }
}
