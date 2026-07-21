package com.zibrinet.split.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zibrinet.split.data.SplitRepository
import com.zibrinet.split.ui.common.appContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class RootGate { LOADING, ONBOARDING, READY }

/** First-launch gate: no participants yet means onboarding. */
class RootViewModel(repository: SplitRepository) : ViewModel() {

    val gate: StateFlow<RootGate> = repository.participants
        .map { if (it.isEmpty()) RootGate.ONBOARDING else RootGate.READY }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootGate.LOADING)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { RootViewModel(appContainer().repository) }
        }
    }
}
