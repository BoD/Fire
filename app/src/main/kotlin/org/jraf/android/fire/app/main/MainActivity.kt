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
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.WindowManager
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ExtractorMediaSource
import com.google.android.exoplayer2.source.LoopingMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveVideoTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import org.jraf.android.fire.R
import org.jraf.android.fire.databinding.MainBinding


class MainActivity : AppCompatActivity() {
    private lateinit var mBinding: MainBinding
    private var mPlayer: SimpleExoPlayer? = null

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

        playVideo()
    }

    private fun playVideo() {
        // 1. Create a default TrackSelector
        val mainHandler = Handler()
        val bandwidthMeter = DefaultBandwidthMeter()
        val videoTrackSelectionFactory = AdaptiveVideoTrackSelection.Factory(bandwidthMeter)
        val trackSelector = DefaultTrackSelector(mainHandler, videoTrackSelectionFactory)
        val loadControl = DefaultLoadControl()

        val player = ExoPlayerFactory.newSimpleInstance(this, trackSelector, loadControl)
        mPlayer = player

        mBinding.player.player = player

        val dataSourceFactory = DefaultDataSourceFactory(this, Util.getUserAgent(this, "Fire"), bandwidthMeter)
        val extractorsFactory = DefaultExtractorsFactory()
        val videoUri = Uri.parse("https://dl.dropboxusercontent.com/u/9317624/fire2.mp4")
        val videoSource = ExtractorMediaSource(videoUri, dataSourceFactory, extractorsFactory, null, null)

        val loopingSource = LoopingMediaSource(videoSource)

        player.prepare(loopingSource)
        player.playWhenReady = true
    }

    override fun onPause() {
        super.onPause()
        mPlayer?.playWhenReady = false
    }

    override fun onResume() {
        super.onPause()
        mPlayer?.playWhenReady = true
    }

    override fun onDestroy() {
        mPlayer?.release()
        super.onDestroy()
    }
}
