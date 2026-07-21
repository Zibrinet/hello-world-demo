package com.zibrinet.split

import android.app.Application
import com.zibrinet.split.di.AppContainer
import com.zibrinet.split.di.DefaultAppContainer

class SplitApp : Application() {
    val container: AppContainer by lazy { DefaultAppContainer(this) }
}
