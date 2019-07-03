package com.loafofpiecrust.turntable.repository

import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.filePath
import com.loafofpiecrust.turntable.repository.local.LocalApi
import com.loafofpiecrust.turntable.repository.remote.StreamCache
import com.loafofpiecrust.turntable.repository.remote.YouTube
import com.loafofpiecrust.turntable.service.OnlineSearchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.firstOrNull
import kotlinx.coroutines.launch
import org.jetbrains.anko.downloadManager
import org.jetbrains.anko.toast

/**
 * Provides streaming urls for a song.
 */
interface StreamProvider {
    suspend fun sourceForSong(song: Song): Song.Media?
}

object StreamProviders: StreamProvider {
    private val LIST: Array<StreamProvider> = arrayOf(
        LocalApi,
        StreamCache
    )

    private val REMOTE: Array<StreamProvider> = arrayOf(
        YouTube
    )

    override suspend fun sourceForSong(song: Song): Song.Media? {
        return try {
            for (provider in LIST) {
                val source = provider.sourceForSong(song)
                if (source != null) {
                    return source
                }
            }

            var finalSource: Song.Media? = null
            for (provider in REMOTE) {
                val source = provider.sourceForSong(song)
                if (source != null) {
                    finalSource = source
                }
            }

            StreamCache.save(song, finalSource)
            finalSource
        } catch (e: Exception) {
            null
        }
    }

    suspend fun canDownload(song: Song): Boolean {
        return LocalApi.sourceForSong(song) != null ||
            OnlineSearchService.instance.findDownload(song).firstOrNull() != null
    }

    suspend fun download(song: Song) {
        val streams = sourceForSong(song)
        val source = streams?.sources?.filter { it.format != "opus" }?.maxBy { it.quality }
        val downloadUrl = source?.url

        if (downloadUrl == null) {
            App.instance.toast("Source for song not found.")
            return
        }

        val req = DownloadManager.Request(Uri.parse(downloadUrl))
        req.setTitle(song.id.displayName)

        val ext = when (source.format) {
            "aac" -> "m4a"
            "opus" -> "webm"
            else -> "mp3"
        }


        GlobalScope.launch(Dispatchers.Main) {
            try {
                req.allowScanningByMediaScanner()
                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                req.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_MUSIC,
                    "${song.id.filePath}.$ext"
                )

                val dlManager = App.instance.downloadManager
                val id = dlManager.enqueue(req)
                OnlineSearchService.instance.addDownload(
                    OnlineSearchService.SongDownload(song, 0, id)
                )
            } catch (e: Exception) {
                App.instance.toast(e.message ?: "Download queue error")
            }
        }
    }
}