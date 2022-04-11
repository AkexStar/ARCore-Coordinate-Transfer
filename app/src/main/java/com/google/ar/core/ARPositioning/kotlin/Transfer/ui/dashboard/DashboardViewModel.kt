package com.google.ar.core.ARPositioning.kotlin.Transfer.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DashboardViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "使用AR会话进行室内定位与坐标采集"
    }
    val text: LiveData<String> = _text
}