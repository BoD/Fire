/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2016 Benoit 'BoD' Lubek (BoD@JRAF.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
@file:Suppress("DEPRECATION")

package org.jraf.android.fire.app.main

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.AbsoluteLayout
import androidx.databinding.DataBindingUtil
import org.jraf.android.fire.R
import org.jraf.android.fire.databinding.MainBinding
import java.io.File
import java.io.IOException

private const val VIDEO_URL = "https://www.dropbox.com/s/je9enadyijldpbk/fire3.mp4?dl=1"

private const val TAG = "fire"

class MainActivity : Activity() {
    private lateinit var mainBinding: MainBinding
    private var mediaPlayer: MediaPlayer? = null
    private var videoWidth: Int? = null
    private var videoHeight: Int? = null
    private var surfaceView: SurfaceView? = null
    private val videoFile by lazy { File(externalCacheDir, "video.mp4") }
    private val downloadManager by lazy { getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    private var isResumed = false
    private val handler = Handler()

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        mainBinding = DataBindingUtil.setContentView(this, R.layout.main)

        mainBinding.root.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LOW_PROFILE

        mainBinding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val videoWidth = videoWidth
            val videoHeight = videoHeight
            if (videoWidth != null && videoHeight != null) adjustSurfaceView(
                videoWidth,
                videoHeight
            )
        }

        if (videoFile.exists()) {
            startPlayback()
        } else {
            startDownload()
        }
    }

    private fun startDownload() {
        // First check if a download is already ongoing
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_RUNNING or
                    DownloadManager.STATUS_PENDING or
                    DownloadManager.STATUS_PAUSED
        )
        val cursor = downloadManager.query(query)
        val downloadOngoing =
            cursor.use {
                it.count > 0
            }
        if (!downloadOngoing) {
            val uri = Uri.parse(VIDEO_URL)
            val request = DownloadManager.Request(uri)
            request.setTitle(getString(R.string.app_name))
            request.setDescription(getString(R.string.downloadTitle))
            request.setVisibleInDownloadsUi(true)
            request.setDestinationUri(Uri.fromFile(videoFile))
            downloadManager.enqueue(request)
        }
        updateDownloadProgressMonitor()
    }


    @SuppressLint("Range")
    private fun updateDownloadProgressMonitor() {
        val query = DownloadManager.Query()
            .setFilterByStatus(
                DownloadManager.STATUS_RUNNING or
                        DownloadManager.STATUS_PENDING or
                        DownloadManager.STATUS_PAUSED
            )
        val cursor = downloadManager.query(query)
        val size: Long
        val downloaded: Long
        cursor.use { c ->
            if (!c.moveToFirst()) return
            size = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            downloaded = c.getLong(c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        }

        // Start playback when X% of the file has been downloaded
        val startPlaybackPercent = 80F

        mainBinding.pgbDownloadProgress.max =
            ((size / 1000L).toFloat() * (startPlaybackPercent / 100F)).toInt()
        mainBinding.pgbDownloadProgress.progress = (downloaded / 1000L).toInt()

        val progressPercent = downloaded.toFloat() / size.toFloat() * 100F
        if (progressPercent >= startPlaybackPercent) {
            startPlayback()
        } else {
            handler.postDelayed({
                if (isResumed) updateDownloadProgressMonitor()
            }, 1000)
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        mediaPlayer?.start()
    }

    override fun onPause() {
        isResumed = false
        mediaPlayer?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        super.onDestroy()
    }

    private val surfaceViewCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            playVideo(holder)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
        }
    }

    private fun startPlayback() {
        mainBinding.root.removeAllViews()
        surfaceView = SurfaceView(this)
        surfaceView!!.holder.addCallback(surfaceViewCallback)
        mainBinding.root.addView(surfaceView)
    }

    private fun playVideo(holder: SurfaceHolder) {
        val mediaPlayer = MediaPlayer()
        this.mediaPlayer = mediaPlayer
        mediaPlayer.setDataSource(videoFile.absolutePath)

        mediaPlayer.setDisplay(holder)
        try {
            mediaPlayer.prepare()
        } catch (e: IOException) {
            Log.e(TAG, "prepare() failed", e)
        }
        mediaPlayer.setOnVideoSizeChangedListener(onVideoSizeChangedListener)
        mediaPlayer.start()

        mediaPlayer.setOnCompletionListener { mp ->
            mp.release()
            startPlayback()
        }
    }

    private val onVideoSizeChangedListener =
        MediaPlayer.OnVideoSizeChangedListener { _, videoWidth, videoHeight ->
            this.videoWidth = videoWidth
            this.videoHeight = videoHeight
            adjustSurfaceView(videoWidth, videoHeight)
        }

    private fun adjustSurfaceView(videoWidth: Int, videoHeight: Int) {
        val parent = mainBinding.root
        val parentWidth = parent.width
        val parentHeight = parent.height

        // Resize surface view according to the video's aspect ratio and by cropping
        val xScale = parentWidth.toFloat() / videoWidth
        val yScale = parentHeight.toFloat() / videoHeight
        val scale = Math.max(xScale, yScale)

        // Now get the dimensions of the video when scaled
        val scaledWidth = scale * videoWidth
        val scaledHeight = scale * videoHeight

        // Center the resulting video
        val x = (scaledWidth - parentWidth) / 2
        val y = (scaledHeight - parentHeight) / 2

        val layoutParams = surfaceView!!.layoutParams as AbsoluteLayout.LayoutParams
        layoutParams.width = scaledWidth.toInt()
        layoutParams.height = scaledHeight.toInt()
        layoutParams.x = (-x).toInt()
        layoutParams.y = (-y).toInt()
        surfaceView!!.layoutParams = layoutParams
    }
}
