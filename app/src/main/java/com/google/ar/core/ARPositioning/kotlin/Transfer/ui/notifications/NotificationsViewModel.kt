package com.google.ar.core.ARPositioning.kotlin.Transfer.ui.notifications

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class NotificationsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "武汉大学测绘学院\n李锦韬 2018302141059\n室内AR测量©2021-2022\nE-mail：lijintao@whu.edu.cn"
    }
    val text: LiveData<String> = _text
}