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
package org.jraf.android.fire.app.main

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.databinding.DataBindingUtil
import android.media.MediaPlayer
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.support.annotation.WorkerThread
import android.support.v7.app.AppCompatActivity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.AbsoluteLayout
import org.jraf.android.fire.R
import org.jraf.android.fire.databinding.MainBinding
import org.jraf.android.util.handler.HandlerUtil
import org.jraf.android.util.log.Log
import org.jraf.android.util.log.LogUtil
import java.io.File

private const val VIDEO_URL = "https://www.dropbox.com/s/7q5cj6omvsu5dhp/fire3.mp4"

class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: MainBinding
    private var mMediaPlayer: MediaPlayer? = null
    private var mVideoWidth: Int? = null
    private var mVideoHeight: Int? = null
    private var mSurfaceView: SurfaceView? = null
    private val mVideoFile by lazy { File(externalCacheDir, "video.mp4") }
    private val mDownloadManager by lazy { getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    private var mReceiverRegistered = false
    private var mIsResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        mBinding = DataBindingUtil.setContentView<MainBinding>(this, R.layout.main)

        mBinding.root.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LOW_PROFILE)

        mBinding.root.viewTreeObserver.addOnGlobalLayoutListener {
            val videoWidth = mVideoWidth
            val videoHeight = mVideoHeight
            if (videoWidth != null && videoHeight != null) adjustSurfaceView(videoWidth, videoHeight)
        }

        Log.d("mVideoFile=$mVideoFile")
        if (mVideoFile.exists()) {
            startPlayback()
        } else {
            startDownload()
        }
    }

    private fun startDownload() {
        // First check if a download is already ongoing
        val query = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED)
        val cursor = mDownloadManager.query(query)
        val downloadOngoing: Boolean
        try {
            downloadOngoing = cursor.count > 0
        } finally {
            cursor.close()
        }
        if (!downloadOngoing) {
            val uri = Uri.parse(VIDEO_URL)
            val request = DownloadManager.Request(uri)
            request.setTitle(getString(R.string.app_name))
            request.setDescription(getString(R.string.downloadTitle))
            request.setVisibleInDownloadsUi(true)
            request.setDestinationUri(Uri.fromFile(mVideoFile))
            mDownloadManager.enqueue(request)
        }
        registerReceiver(mDownloadBroadcastReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        mReceiverRegistered = true
        updateDownloadProgressMonitor()
    }


    private fun updateDownloadProgressMonitor() {
        val query = DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED)
        val cursor = mDownloadManager.query(query)
        val size: Long
        val downloaded: Long
        try {
            if (!cursor.moveToFirst()) return
            size = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            downloaded = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        } finally {
            cursor.close()
        }

        // Start playback when X% of the file has been downloaded
        val startPlaybackPercent = 15F

        mBinding.pgbDownloadProgress.max = ((size / 1000L).toFloat() * (startPlaybackPercent / 100F)).toInt()
        mBinding.pgbDownloadProgress.progress = (downloaded / 1000L).toInt()

        val progressPercent = downloaded.toFloat() / size.toFloat() * 100F
        if (progressPercent >= startPlaybackPercent) {
            startPlayback()
        } else {
            HandlerUtil.getMainHandler().postDelayed({
                if (mIsResumed) updateDownloadProgressMonitor()
            }, 1000)
        }
    }

    private val mDownloadBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE == intent.action) {
                Log.d("Download complete")
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
                // Do the work in a background thread because it does disk i/o
                val result = goAsync()
                object : AsyncTask<Unit, Unit, Unit>() {
                    override fun doInBackground(vararg params: Unit) {
                        handleDownloadComplete(downloadId)
                        result.finish()
                    }
                }.execute()
            }
        }
    }

    @WorkerThread
    private fun handleDownloadComplete(downloadId: Long) {
        Log.d("downloadId=$downloadId")
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = mDownloadManager.query(query)
        try {
            cursor.moveToFirst()
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            Log.d("status=${LogUtil.getConstantName(DownloadManager::class.java, status, "STATUS")}")
            val fileName = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_DESCRIPTION))
            when (status) {
                DownloadManager.STATUS_FAILED -> onFail(fileName)

                DownloadManager.STATUS_SUCCESSFUL -> {
                    val downloadedFilePath = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME))
                    onSuccess(downloadedFilePath)
                }
            }
        } finally {
            cursor.close()
        }
    }

    private fun onFail(fileName: String) {
        Log.w("fileName=$fileName")
        // TODO Error handling
    }

    private fun onSuccess(downloadedFilePath: String) {
        Log.d("downloadedFilePath=$downloadedFilePath")
    }

    override fun onResume() {
        super.onResume()
        mIsResumed = true
        mMediaPlayer?.start()
    }

    override fun onPause() {
        mIsResumed = false
        mMediaPlayer?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        mMediaPlayer?.release()
        if (mReceiverRegistered) unregisterReceiver(mDownloadBroadcastReceiver)
        super.onDestroy()
    }

    private val mSurfaceViewCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            playVideo(holder)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
        }
    }

    private fun startPlayback() {
        mBinding.root.removeAllViews()
        mSurfaceView = SurfaceView(this)
        mSurfaceView!!.holder.addCallback(mSurfaceViewCallback)
        mBinding.root.addView(mSurfaceView)
    }

    private fun playVideo(holder: SurfaceHolder) {
        val mediaPlayer = MediaPlayer()
        mMediaPlayer = mediaPlayer
        mediaPlayer.setDataSource(mVideoFile.absolutePath)

        mediaPlayer.setDisplay(holder)
        mediaPlayer.prepare()
        mediaPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener)
        mediaPlayer.start()

        mediaPlayer.setOnCompletionListener { mp ->
            Log.d("Looping")
            mp.release()
            startPlayback()
        }
    }

    private val mOnVideoSizeChangedListener = MediaPlayer.OnVideoSizeChangedListener { mp, videoWidth, videoHeight ->
        Log.d("videoWidth=$videoWidth videoHeight=$videoHeight")
        mVideoWidth = videoWidth
        mVideoHeight = videoHeight
        adjustSurfaceView(videoWidth, videoHeight)
    }

    private fun adjustSurfaceView(videoWidth: Int, videoHeight: Int) {
        val parent = mBinding.root
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

        val layoutParams = mSurfaceView!!.layoutParams as AbsoluteLayout.LayoutParams
        layoutParams.width = scaledWidth.toInt()
        layoutParams.height = scaledHeight.toInt()
        layoutParams.x = (-x).toInt()
        layoutParams.y = (-y).toInt()
        mSurfaceView!!.layoutParams = layoutParams
    }
}

