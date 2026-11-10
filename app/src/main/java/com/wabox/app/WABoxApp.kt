package com.wabox.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.launch

class WABoxApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.liveObserver)
        container.appScope.launch {
            container.scheduler.applyBackgroundRefresh(container.settings.current().backgroundRefresh)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(180)
            .build()
}
