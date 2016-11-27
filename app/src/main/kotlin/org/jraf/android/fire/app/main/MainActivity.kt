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

import android.databinding.DataBindingUtil
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.AbsoluteLayout
import org.jraf.android.fire.R
import org.jraf.android.fire.databinding.MainBinding
import org.jraf.android.util.log.Log


class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: MainBinding
    private var mMediaPlayer: MediaPlayer? = null
    private var mVideoWidth: Int? = null
    private var mVideoHeight: Int? = null

    private var surfaceView: SurfaceView? = null

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

        surfaceView = SurfaceView(this)
        surfaceView!!.holder.addCallback(mSurfaceViewCallback)
        mBinding.root.addView(surfaceView)
    }

    override fun onPause() {
        super.onPause()
        mMediaPlayer?.pause()
    }

    override fun onResume() {
        super.onPause()
        mMediaPlayer?.start()
    }

    override fun onDestroy() {
        mMediaPlayer?.release()
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

    private fun playVideo(holder: SurfaceHolder) {
        val mediaPlayer = MediaPlayer()
        mMediaPlayer = mediaPlayer
        mediaPlayer.setDataSource("http://jraf.org/static/tmp/fire2.mp4")

        mediaPlayer.setDisplay(holder)
        mediaPlayer.prepare()
        mediaPlayer.setOnVideoSizeChangedListener(mOnVideoSizeChangedListener)
        mediaPlayer.start()

        mediaPlayer.setOnCompletionListener { mp ->
            Log.d("Looping")
            mp.release()

            mBinding.root.removeAllViews()
            surfaceView = SurfaceView(this)
            surfaceView!!.holder.addCallback(mSurfaceViewCallback)
            mBinding.root.addView(surfaceView)
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

        val layoutParams = surfaceView!!.layoutParams as AbsoluteLayout.LayoutParams
        layoutParams.width = scaledWidth.toInt()
        layoutParams.height = scaledHeight.toInt()
        layoutParams.x = (-x).toInt()
        layoutParams.y = (-y).toInt()
        surfaceView!!.layoutParams = layoutParams
    }
}
