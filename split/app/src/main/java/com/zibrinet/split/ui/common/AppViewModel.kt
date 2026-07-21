package com.zibrinet.split.ui.common

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.zibrinet.split.SplitApp
import com.zibrinet.split.di.AppContainer

/** Pulls the app container out of CreationExtras for ViewModel factories. */
fun CreationExtras.appContainer(): AppContainer =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SplitApp).container
