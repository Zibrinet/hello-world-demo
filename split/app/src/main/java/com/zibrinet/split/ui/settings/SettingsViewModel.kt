package com.zibrinet.split.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zibrinet.split.data.SplitRepository
import com.zibrinet.split.data.settings.SettingsRepository
import com.zibrinet.split.ui.common.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val loading: Boolean = true,
    val selfName: String = "",
    val otherName: String = "",
    val homeCurrency: String = SettingsRepository.DEFAULT_HOME_CURRENCY,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = selfName.isNotBlank() && otherName.isNotBlank()
}

class SettingsViewModel(
    private val repository: SplitRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val participants = repository.participants.first()
            _state.update {
                it.copy(
                    loading = false,
                    selfName = participants.firstOrNull { p -> p.isSelf }?.name.orEmpty(),
                    otherName = participants.firstOrNull { p -> !p.isSelf }?.name.orEmpty(),
                    homeCurrency = settings.homeCurrency.first(),
                )
            }
        }
    }

    fun setSelfName(value: String) = _state.update { it.copy(selfName = value, saved = false) }

    fun setOtherName(value: String) = _state.update { it.copy(otherName = value, saved = false) }

    fun setHomeCurrency(code: String) = _state.update { it.copy(homeCurrency = code, saved = false) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            repository.renameParticipants(s.selfName, s.otherName)
            settings.setHomeCurrency(s.homeCurrency)
            _state.update { it.copy(saved = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SettingsViewModel(container.repository, container.settings)
            }
        }
    }
}
