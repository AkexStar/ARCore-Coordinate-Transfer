/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.examples.kotlin.common.helpers

import android.app.Activity
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Session
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper
import com.google.ar.core.exceptions.CameraNotAvailableException

/**
 * Manages an ARCore Session using the Android Lifecycle API. Before starting a Session, this class
 * requests installation of Google Play Services for AR if it's not installed or not up to date and
 * asks the user for required permissions if necessary.
 * 使用 Android Lifecycle API 管理 ARCore 会话。在开始会话之前，
 * 如果 Google Play Services for AR 未安装或不是最新的，
 * 此类会请求安装它，并在必要时要求用户提供所需的权限。
 */
class ARCoreSessionLifecycleHelper(
  val activity: Activity,
  val features: Set<Session.Feature> = setOf()
) : DefaultLifecycleObserver {
  var installRequested = false
  var session: Session? = null

  /**
   * Creating a session may fail. In this case, session will remain null, and this function will be
   * called with an exception.创建会话可能会失败。在这种情况下，会话将保持为空，并且该函数将被异常调用。
   *
   * See
   * [the `Session` constructor](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#Session(android.content.Context)
   * ) for more details.
   */
  var exceptionCallback: ((Exception) -> Unit)? = null

  /**
   * Before `Session.resume()` is called, a session must be configured. Use
   * 在调用 `Session.resume()` 之前，必须配置一个会话。
   * [`Session.configure`](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#configure-config)
   * or
   * [`setCameraConfig`](https://developers.google.com/ar/reference/java/com/google/ar/core/Session#setCameraConfig-cameraConfig)
   */
  var beforeSessionResume: ((Session) -> Unit)? = null

  /**
   * Attempts to create a session. If Google Play Services for AR is not installed or not up to
   * date, request installation.
   * 尝试创建会话。如果 Google Play Services for AR 未安装或不是最新的，请请求安装。
   * @return null when the session cannot be created due to a lack of the CAMERA permission or when
   * Google Play Services for AR is not installed or up to date, or when session creation fails for
   * any reason. In the case of a failure, [exceptionCallback] is invoked with the failure exception.
   */
  fun tryCreateSession(): Session? {
    // The app must have been given the CAMERA permission. If we don't have it yet, request it.
    // 该应用程序必须已获得 CAMERA 权限。如果我们还没有，请索取。
    if (!CameraPermissionHelper.hasCameraPermission(activity)) {
      CameraPermissionHelper.requestCameraPermission(activity)
      return null
    }

    return try {
      // Request installation if necessary. 没有ARCore，必要时请求安装。
      when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)!!) {
        ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
          installRequested = true
          // tryCreateSession will be called again, so we return null for now.
          // tryCreateSession 将被再次调用，所以我们现在返回 null。
          return null
        }
        ArCoreApk.InstallStatus.INSTALLED -> {
          // Left empty; nothing needs to be done.
          // 已经安装了。留空；什么都不需要做。
        }
      }

      // Create a session if Google Play Services for AR is installed and up to date.
      // 如果安装了适用于 AR 的 Google Play 服务并且是最新的，则创建一个会话。
      Session(activity, features) // features初始状态是空的
    } catch (e: Exception) {
      exceptionCallback?.invoke(e)
      null
    }
  }

  override fun onResume(owner: LifecycleOwner) { //从暂停中重启
    val session = this.session ?: tryCreateSession() ?: return
    try {
      beforeSessionResume?.invoke(session)
      session.resume()
      this.session = session
    } catch (e: CameraNotAvailableException) {
      exceptionCallback?.invoke(e)
    }
  }

  // 暂停
  override fun onPause(owner: LifecycleOwner) {
    session?.pause()
  }

  override fun onDestroy(owner: LifecycleOwner) {
    // Explicitly close the ARCore session to release native resources.显式关闭 ARCore 会话以释放原生资源。
    // Review the API reference for important considerations before calling close() in apps with
    // more complicated lifecycle requirements:
    // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
    session?.close()
    session = null
  }

  fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    results: IntArray
  ) {
    if (!CameraPermissionHelper.hasCameraPermission(activity)) { // 还是没有拿到摄像头的使用权
      // Use toast instead of snackbar here since the activity will exit. 使用toast给出一个提示
      Toast.makeText(
          activity,
          "需要相机权限才能运行此应用程序！", // Camera permission is needed to run this application
          Toast.LENGTH_LONG
        )
        .show()
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(activity)) {
        // Permission denied with checking "Do not ask again". 选中“不再询问”后，权限被拒绝。
        CameraPermissionHelper.launchPermissionSettings(activity)
      }
      activity.finish() // 直接关闭App
    }
  }
}
