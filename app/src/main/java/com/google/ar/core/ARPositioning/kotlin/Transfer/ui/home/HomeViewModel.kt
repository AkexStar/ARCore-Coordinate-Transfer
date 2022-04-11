package com.google.ar.core.ARPositioning.kotlin.Transfer.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "建立工程方便后续文件管理"
    }
    val text: LiveData<String> = _text
}