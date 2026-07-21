package com.zibrinet.split.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zibrinet.split.data.SplitRepository
import com.zibrinet.split.ui.common.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingState(
    val selfName: String = "",
    val otherName: String = "",
    val saving: Boolean = false,
) {
    val canContinue: Boolean get() = selfName.isNotBlank() && otherName.isNotBlank() && !saving
}

class OnboardingViewModel(private val repository: SplitRepository) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun setSelfName(value: String) = _state.update { it.copy(selfName = value) }

    fun setOtherName(value: String) = _state.update { it.copy(otherName = value) }

    fun submit() {
        val s = _state.value
        if (!s.canContinue) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.seedParticipants(s.selfName, s.otherName)
            // The root gate flips to the ledger when participants appear.
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { OnboardingViewModel(appContainer().repository) }
        }
    }
}
