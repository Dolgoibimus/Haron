package com.vamp.haron.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.audio.FlacTagEditor
import com.vamp.haron.common.audio.Id3TagEditor
import com.vamp.haron.common.audio.M4aTagEditor
import com.vamp.haron.common.audio.OggTagEditor
import com.vamp.haron.common.util.ThumbnailCache
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

sealed class CoverResult {
    data object Searching : CoverResult()
    data class Found(
        val bitmap: Bitmap,
        val imageBytes: ByteArray,
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val year: String = "",
        val genre: String = ""
    ) : CoverResult()
    data object NotFound : CoverResult()
    data object Saved : CoverResult()
    data class Error(val message: String) : CoverResult()
}

class FetchAlbumCoverUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    operator fun invoke(path: String, manualQuery: String? = null): Flow<CoverResult> = flow {
        emit(CoverResult.Searching)

        val query: String
        if (!manualQuery.isNullOrBlank()) {
            query = manualQuery.trim()
            EcosystemLogger.d(TAG, "Manual search query: '$query'")
        } else {
            // Read artist + album from file
            val retriever = MediaMetadataRetriever()
            val artist: String?
            val album: String?
            try {
                retriever.setDataSource(path)
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            } finally {
                retriever.release()
            }

            if (artist.isNullOrBlank() && album.isNullOrBlank()) {
                EcosystemLogger.d(TAG, "No artist/album tags in file, cannot search")
                emit(CoverResult.NotFound)
                return@flow
            }

            query = listOfNotNull(artist, album).joinToString(" ")
            EcosystemLogger.d(TAG, "Searching cover for: '$query'")
        }

        // Try Deezer first, then iTunes
        val searchResult = tryDeezer(query) ?: tryItunes(query)

        if (searchResult == null) {
            EcosystemLogger.i(TAG, "Nothing found for '$query'")
            emit(CoverResult.NotFound)
            return@flow
        }

        val bitmap = if (searchResult.imageBytes != null) {
            BitmapFactory.decodeByteArray(searchResult.imageBytes, 0, searchResult.imageBytes.size)
        } else null

        if (bitmap == null && searchResult.imageBytes != null) {
            EcosystemLogger.e(TAG, "Failed to decode downloaded image")
        }

        EcosystemLogger.i(TAG, "Found: artist=${searchResult.artist}, album=${searchResult.album}, " +
            "title=${searchResult.title}, year=${searchResult.year}, genre=${searchResult.genre}, " +
            "hasCover=${bitmap != null}")

        if (bitmap == null && searchResult.title.isEmpty() && searchResult.artist.isEmpty()) {
            emit(CoverResult.NotFound)
            return@flow
        }

        emit(CoverResult.Found(
            bitmap = bitmap ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888),
            imageBytes = searchResult.imageBytes ?: ByteArray(0),
            title = searchResult.title,
            artist = searchResult.artist,
            album = searchResult.album,
            year = searchResult.year,
            genre = searchResult.genre
        ))
    }.flowOn(Dispatchers.IO)

    fun saveCover(path: String, imageBytes: ByteArray): Flow<CoverResult> = flow {
        try {
            val file = File(path)
            val ext = file.extension.lowercase()

            val success = when (ext) {
                "mp3" -> Id3TagEditor.writeCoverArt(file, imageBytes, "image/jpeg")
                "flac" -> FlacTagEditor.writeCoverArt(file, imageBytes, "image/jpeg")
                "ogg", "oga", "opus" -> OggTagEditor.writeCoverArt(file, imageBytes, "image/jpeg")
                "m4a", "m4b", "mp4", "aac", "alac" -> M4aTagEditor.writeCoverArt(file, imageBytes, "image/jpeg")
                else -> {
                    EcosystemLogger.e(TAG, "Cover writing not supported for: $ext")
                    emit(CoverResult.Error("Cover writing not supported for $ext"))
                    return@flow
                }
            }
            if (success) {
                // Invalidate thumbnail cache so list shows the new cover
                ThumbnailCache.remove(path)
                EcosystemLogger.i(TAG, "Cover saved to: $path")
                emit(CoverResult.Saved)
            } else {
                emit(CoverResult.Error("Failed to write cover art"))
            }
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "saveCover failed: ${e.message}")
            emit(CoverResult.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    private data class SearchResult(
        val imageBytes: ByteArray? = null,
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val year: String = "",
        val genre: String = ""
    )

    private fun tryDeezer(query: String): SearchResult? {
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://api.deezer.com/search/track?q=$encodedQuery&limit=1"
            EcosystemLogger.d(TAG, "Deezer track search: $url")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                EcosystemLogger.d(TAG, "Deezer HTTP ${response.code}")
                response.close()
                return null
            }

            val body = response.body?.string() ?: return null
            response.close()
            val json = JSONObject(body)
            val data = json.optJSONArray("data")
            if (data == null || data.length() == 0) {
                EcosystemLogger.d(TAG, "Deezer: no results")
                return null
            }

            val track = data.getJSONObject(0)
            val trackTitle = track.optString("title", "")
            val artistObj = track.optJSONObject("artist")
            val artistName = artistObj?.optString("name", "") ?: ""
            val albumObj = track.optJSONObject("album")
            val albumTitle = albumObj?.optString("title", "") ?: ""
            val coverUrl = albumObj?.optString("cover_big", "") ?: ""
            val albumId = albumObj?.optLong("id", 0L) ?: 0L

            EcosystemLogger.d(TAG, "Deezer track: '$trackTitle' by '$artistName', album='$albumTitle', albumId=$albumId")

            // Fetch album details for year and genre
            var year = ""
            var genre = ""
            if (albumId > 0) {
                try {
                    val albumUrl = "https://api.deezer.com/album/$albumId"
                    val albumReq = Request.Builder().url(albumUrl).build()
                    val albumResp = client.newCall(albumReq).execute()
                    if (albumResp.isSuccessful) {
                        val albumBody = albumResp.body?.string()
                        albumResp.close()
                        if (albumBody != null) {
                            val albumJson = JSONObject(albumBody)
                            val releaseDate = albumJson.optString("release_date", "")
                            if (releaseDate.length >= 4) year = releaseDate.substring(0, 4)
                            val genres = albumJson.optJSONObject("genres")?.optJSONArray("data")
                            if (genres != null && genres.length() > 0) {
                                genre = genres.getJSONObject(0).optString("name", "")
                            }
                        }
                    } else {
                        albumResp.close()
                    }
                } catch (e: Exception) {
                    EcosystemLogger.d(TAG, "Deezer album details error: ${e.message}")
                }
            }

            val imageBytes = if (coverUrl.isNotBlank()) downloadImage(coverUrl) else null
            EcosystemLogger.d(TAG, "Deezer result: title=$trackTitle, artist=$artistName, album=$albumTitle, year=$year, genre=$genre, hasCover=${imageBytes != null}")

            SearchResult(
                imageBytes = imageBytes,
                title = trackTitle,
                artist = artistName,
                album = albumTitle,
                year = year,
                genre = genre
            )
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "Deezer error: ${e.message}")
            null
        }
    }

    private fun tryItunes(query: String): SearchResult? {
        return try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&media=music&entity=musicTrack&limit=1"
            EcosystemLogger.d(TAG, "iTunes track search: $url")

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                EcosystemLogger.d(TAG, "iTunes HTTP ${response.code}")
                response.close()
                return null
            }

            val body = response.body?.string() ?: return null
            response.close()
            val json = JSONObject(body)
            val results = json.optJSONArray("results")
            if (results == null || results.length() == 0) {
                EcosystemLogger.d(TAG, "iTunes: no results")
                return null
            }

            val track = results.getJSONObject(0)
            val trackName = track.optString("trackName", "")
            val artistName = track.optString("artistName", "")
            val collectionName = track.optString("collectionName", "")
            val releaseDate = track.optString("releaseDate", "") // "2020-01-15T12:00:00Z"
            val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""
            val genre = track.optString("primaryGenreName", "")
            val artworkUrl = track.optString("artworkUrl100", "")

            val imageBytes = if (artworkUrl.isNotBlank()) {
                val hiResUrl = artworkUrl.replace("100x100", "600x600")
                EcosystemLogger.d(TAG, "iTunes cover URL: $hiResUrl")
                downloadImage(hiResUrl)
            } else null

            EcosystemLogger.d(TAG, "iTunes result: title=$trackName, artist=$artistName, album=$collectionName, year=$year, genre=$genre, hasCover=${imageBytes != null}")

            SearchResult(
                imageBytes = imageBytes,
                title = trackName,
                artist = artistName,
                album = collectionName,
                year = year,
                genre = genre
            )
        } catch (e: Exception) {
            EcosystemLogger.e(TAG, "iTunes error: ${e.message}")
            null
        }
    }

    private fun downloadImage(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        return if (response.isSuccessful) {
            val bytes = response.body?.bytes()
            response.close()
            bytes
        } else {
            response.close()
            null
        }
    }

    companion object {
        private const val TAG = "Haron/AlbumCover"
    }
}
