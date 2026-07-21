package com.zibrinet.split.di

import android.content.Context
import com.zibrinet.split.data.RoomSplitRepository
import com.zibrinet.split.data.SplitRepository
import com.zibrinet.split.data.local.SplitDatabase
import com.zibrinet.split.data.settings.SettingsRepository

/** Hand-rolled DI: one shared ledger, one database, no framework needed. */
interface AppContainer {
    val repository: SplitRepository
    val settings: SettingsRepository
}

class DefaultAppContainer(private val appContext: Context) : AppContainer {
    private val database: SplitDatabase by lazy { SplitDatabase.build(appContext) }

    override val repository: SplitRepository by lazy { RoomSplitRepository(database) }

    override val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
}
