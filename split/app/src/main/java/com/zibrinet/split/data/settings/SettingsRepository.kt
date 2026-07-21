package com.zibrinet.split.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings"
)

/** App-level preferences. Participant names live in Room, not here. */
class SettingsRepository(private val context: Context) {

    private val homeCurrencyKey = stringPreferencesKey("home_currency")

    /** ISO 4217 code of the currency new expenses default to. */
    val homeCurrency: Flow<String> =
        context.settingsDataStore.data.map { it[homeCurrencyKey] ?: DEFAULT_HOME_CURRENCY }

    suspend fun setHomeCurrency(code: String) {
        context.settingsDataStore.edit { it[homeCurrencyKey] = code }
    }

    companion object {
        const val DEFAULT_HOME_CURRENCY = "THB"
    }
}
