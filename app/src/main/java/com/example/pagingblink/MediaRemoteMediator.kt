package com.example.pagingblink

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import kotlinx.coroutines.delay

const val TOTAL_ITEMS = 1000

/**
 * Fetches pages (here: fabricated with a
 * simulated network delay) and writes them into Room. Every insert invalidates the Room
 * PagingSource, which re-emits fresh PagingData — the re-emission is what drives the blink.
 */
@OptIn(ExperimentalPagingApi::class)
class MediaRemoteMediator(
    private val db: AppDatabase,
) : RemoteMediator<Int, MediaEntity>() {

    private var nextIndex = 0

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, MediaEntity>,
    ): MediatorResult {
        when (loadType) {
            LoadType.REFRESH -> nextIndex = 0
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
            LoadType.APPEND -> {
                if (nextIndex >= TOTAL_ITEMS) {
                    return MediatorResult.Success(endOfPaginationReached = true)
                }
            }
        }

        val limit = when (loadType) {
            LoadType.REFRESH -> state.config.initialLoadSize
            else -> state.config.pageSize
        }

        // Simulate network latency.
        delay(400)

        val start = nextIndex
        val end = minOf(start + limit, TOTAL_ITEMS)
        val entities = (start until end).map { i ->
            MediaEntity(
                id = i.toString(),
                messageId = i.toString(),
                // Descending timestamp so ORDER BY sharedAtMilli DESC keeps index 0 at top.
                sharedAtMilli = (TOTAL_ITEMS - i).toLong(),
                imageUrl = "https://picsum.photos/seed/$i/300/300",
            )
        }

        db.withTransaction {
            if (loadType == LoadType.REFRESH) {
                db.mediaDao().clear()
            }
            db.mediaDao().insertAll(entities)
        }
        nextIndex = end

        return MediatorResult.Success(endOfPaginationReached = end >= TOTAL_ITEMS)
    }
}
