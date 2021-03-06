/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.core.ARPositioning.kotlin.Transfer

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.ar.core.*
import com.google.ar.core.ARPositioning.java.common.helpers.CameraPermissionHelper
import com.google.ar.core.ARPositioning.java.common.helpers.DepthSettings
import com.google.ar.core.ARPositioning.java.common.helpers.FullScreenHelper
import com.google.ar.core.ARPositioning.java.common.helpers.InstantPlacementSettings
import com.google.ar.core.ARPositioning.java.common.samplerender.SampleRender
import com.google.ar.core.ARPositioning.kotlin.common.helpers.ARCoreSessionLifecycleHelper
import com.google.ar.core.Config.InstantPlacementMode
import com.google.ar.core.exceptions.*
import java.io.*
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*


/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3D model.
 * ???????????????????????????????????????????????????ARCore API?????????????????????AR??????????????????
 * ??????????????????????????????????????????????????????????????????????????????????????????????????????
 */
class MainActivity : AppCompatActivity() {
  companion object {
    private const val TAG = "HelloArActivity"
    private const val MP4_VIDEO_MIME_TYPE = "video/mp4"
    private const val REQUEST_WRITE_EXTERNAL_STORAGE = 1
    private const val REQUEST_MP4_SELECTOR = 1
    const val TEXTVIEW_CLEAN = 0
    const val TEXTVIEW_UPDATE = 1
    const val FILE_TIME_PATTERN = "MMdd-HHmmss"

    private val ANCHOR_TRACK_ID = UUID.fromString("53069eb5-21ef-4946-b71c-6ac4979216a6")
    private const val ANCHOR_TRACK_MIME_TYPE = "application/recording-playback-anchor"
  }
  lateinit var arCoreSessionHelper: ARCoreSessionLifecycleHelper
  lateinit var view: ArView
  lateinit var renderer: HelloArRenderer
  lateinit var textView: TextView
  private lateinit var inputPointName: EditText
  private lateinit var cameraDataFileName: String
  private lateinit var anchorDataFileName: String
  lateinit var markPointDataFileName: String

  val instantPlacementSettings = InstantPlacementSettings()
  val depthSettings = DepthSettings()

  lateinit var imageDatabase: AugmentedImageDatabase


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val projectName = getSharedPreferences("PROJECT",Context.MODE_PRIVATE).getString("name","Default")
    cameraDataFileName = "$projectName-CamData.txt"
    anchorDataFileName = "$projectName-TraData.txt"
    markPointDataFileName = "$projectName-MPData.txt"
    // Setup ARCore session lifecycle helper and configuration.
    // ??????ARCore?????????????????????????????????????????????
    arCoreSessionHelper = ARCoreSessionLifecycleHelper(this)
    // If Session creation or Session.resume() fails, display a message and log detailed information.
    // ???????????????????????????????????????????????????????????????
    arCoreSessionHelper.exceptionCallback =
      { exception ->
        val message =
          when (exception) {
            is UnavailableUserDeclinedInstallationException ->
              "??????AR??????Google Play??????" // Please install Google Play Services for AR
            is UnavailableApkTooOldException -> "ARCore????????????" // Please update ARCore
            is UnavailableSdkTooOldException -> "App???SDK?????????App????????????" // Please update this app
            is UnavailableDeviceNotCompatibleException -> "??????????????????AR" //This device does not support AR
            is CameraNotAvailableException -> "??????????????????????????????????????????" //Camera not available. Try restarting the app.
            else -> "Failed to create AR session: $exception"
          }
        Log.e(TAG, "ARCore threw an exception", exception)
        view.snackbarHelper.showError(this, message)
      }
    // ????????????session????????????

    // Configure session features, including: Lighting Estimation, Depth mode, Instant Placement.
    // ???????????????????????????????????????????????????????????????????????????
    arCoreSessionHelper.beforeSessionResume = ::configureSession
    lifecycle.addObserver(arCoreSessionHelper)
    // Set up the Hello AR renderer.
    // ??????AR?????????
    renderer = HelloArRenderer(this)
    lifecycle.addObserver(renderer)
    // Set up Hello AR UI.
    // ??????UI???????????????ArView
    view = ArView(this)
    lifecycle.addObserver(view)
    setContentView(view.root)
    textView = findViewById<View>(R.id.textView) as TextView
    inputPointName = findViewById<View>(R.id.MarkIDInput) as EditText
    // Sets up an example renderer using our HelloARRenderer.
    // ??????????????? HelloARRenderer ????????????????????????
    SampleRender(view.surfaceView, renderer, assets)
    // ???????????????????????????????????????
    depthSettings.onCreate(this)
    instantPlacementSettings.onCreate(this)
    // ????????????session?????????null
  }
  class MyHandler(var weakReferenceActivity:WeakReference<MainActivity>): Handler(Looper.getMainLooper()){
    override fun handleMessage(msg: Message) {
      super.handleMessage(msg)
      weakReferenceActivity.get()?.run {
        when (msg.what){
          TEXTVIEW_CLEAN -> textView.text =""
          TEXTVIEW_UPDATE -> { textView.text = msg.obj as String }
        }
      }
    }
  }
  val mHandler = MyHandler(WeakReference(this))//???????????????????????????

  fun myCameraLog(inputText: String){
    try {
      val time = LocalDateTime.now()
      val output = openFileOutput(cameraDataFileName, Context.MODE_APPEND)
      val writer = BufferedWriter(OutputStreamWriter(output))
      writer.use {
        it.write("$time\t$inputText\n") }
    } catch (e:IOException){
      Log.e(TAG, "????????????data", e)
    }
  }
  fun myLogMessage(inputText: String){
    try {
      val output = openFileOutput(cameraDataFileName, Context.MODE_APPEND)
      val writer = BufferedWriter(OutputStreamWriter(output))
      writer.use {
        it.write("-C$inputText") }
      writer.close()
    } catch (e:IOException){
      Log.e(TAG, "????????????data", e)
    }
    try {
      val output = openFileOutput(anchorDataFileName, Context.MODE_APPEND)
      val writer = BufferedWriter(OutputStreamWriter(output))
      writer.use {
        it.write("-A$inputText") }
      writer.close()
    } catch (e:IOException){
      Log.e(TAG, "????????????data", e)
    }
    try {
      val output = openFileOutput(markPointDataFileName, Context.MODE_APPEND)
      val writer = BufferedWriter(OutputStreamWriter(output))
      writer.use {
        it.write("-M$inputText") }
      writer.close()
    } catch (e:IOException){
      Log.e(TAG, "????????????data", e)
    }
  }
  fun myTrackableLog(inputText: String){
    try {
      val time = LocalDateTime.now()
      val output = openFileOutput(anchorDataFileName, Context.MODE_APPEND)
      val writer = BufferedWriter(OutputStreamWriter(output))
      writer.use {
        it.write("$time\t$inputText\n") }
    } catch (e:IOException){
      Log.e(TAG, "????????????data", e)
    }
  }

  fun onClickSetPoint(view: View?) {
    Log.d(TAG, "onClickSetPoint")
    try {
      val output = openFileOutput(markPointDataFileName, Context.MODE_APPEND)
      val writer = BufferedWriter(OutputStreamWriter(output))
      writer.use {
        val dateFormat = SimpleDateFormat(FILE_TIME_PATTERN, Locale.PRC).format(Date()).toString()
        val pointName = inputPointName.text.toString()
        if (pointName == ""){
          Toast.makeText(this, "??????????????????", Toast.LENGTH_SHORT).show()
          return
        }
        val value = pointName.toInt()+1
        inputPointName.setText(value.toString())
        it.write("$dateFormat\t"+pointName+"\t"+renderer.cameraStatus.toString()+"\n")
      }
      writer.close()
      Toast.makeText(this, "?????????????????????", Toast.LENGTH_SHORT).show()
    } catch (e:IOException){
      Log.e(TAG, "????????????data", e)
    }
  }

  fun onClickAugmentedIMG(view: View?){
    try {
//      imageDatabase = this.assets.open("imgdb/default.imgdb").use {
//        Log.d(TAG, it.toString())
//        Log.d(TAG, arCoreSessionHelper.session.toString())
//        AugmentedImageDatabase.deserialize(arCoreSessionHelper.session, it)
//      }
      imageDatabase = AugmentedImageDatabase(arCoreSessionHelper.session)
      val bitmap = assets.open("imgdb/000.jpg").use { BitmapFactory.decodeStream(it) }
// If the physical size of the image is not known, use addImage(String, Bitmap) instead, at the
// expense of an increased image detection time.????????????????????????????????????????????? addImage(String, Bitmap)?????????????????????????????????
      val imageWidthInMeters = 0.156f // 10 cm
      val dogIndex = imageDatabase.addImage("earth", bitmap, imageWidthInMeters)
      val config = Config(arCoreSessionHelper.session)
      config.augmentedImageDatabase = imageDatabase
      config.focusMode = Config.FocusMode.AUTO
      config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
      // Depth API is used if it is configured in Hello AR's settings.
      // ????????? Hello AR ??????????????????????????? API?????????????????????
      config.depthMode =
        if (arCoreSessionHelper.session?.isDepthModeSupported(Config.DepthMode.AUTOMATIC) == true) {
          Config.DepthMode.AUTOMATIC
        } else {
          Config.DepthMode.DISABLED
        }
      // ????????????????????????????????????????????? Instant Placement???
      config.instantPlacementMode =
        if (instantPlacementSettings.isInstantPlacementEnabled) {
          InstantPlacementMode.LOCAL_Y_UP
        } else {
          InstantPlacementMode.DISABLED
        }
      pauseARCoreSession()
      //???????????????????????????????????????????????????
      arCoreSessionHelper.session?.configure(config)
      resumeARCoreSession()
      Log.d(TAG, arCoreSessionHelper.session?.config?.augmentedImageDatabase?.numImages.toString())
    }catch (e:IOException){
      Log.e(TAG,"Can't build imgdb!")
    }
  }

  // Configure the session, using Lighting Estimation, and Depth mode.
  // ????????????????????????????????????????????????
  fun configureSession(session: Session) {
    session.configure(
      session.config.apply {
        focusMode = Config.FocusMode.AUTO
        lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
        // Depth API is used if it is configured in Hello AR's settings.
        // ????????? Hello AR ??????????????????????????? API?????????????????????
        depthMode =
          if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            Config.DepthMode.AUTOMATIC
          } else {
            Config.DepthMode.DISABLED
          }
        // Instant Placement is used if it is configured in Hello AR's settings.
        // ????????? Hello AR ??????????????????????????????????????? Instant Placement???
        instantPlacementMode =
          if (instantPlacementSettings.isInstantPlacementEnabled) {
            InstantPlacementMode.LOCAL_Y_UP
          } else {
            InstantPlacementMode.DISABLED
          }
//        val imageDatabase = assets.open("imgdb/default.imgdb").use {
//          AugmentedImageDatabase.deserialize(session, it)
//        }
//        augmentedImageDatabase = imageDatabase
      }
    )
  }

  // ?????????????????????????????????
  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<String>,
    results: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, results)
    if (!CameraPermissionHelper.hasCameraPermission(this)) { //?????????????????????????????????
      // Use toast instead of snackbar here since the activity will exit.
      // ????????????????????????????????????????????? toast ????????? snackbar???
      Toast.makeText(this, "??????????????????????????????????????????", Toast.LENGTH_LONG)
        .show()
      if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
        // Permission denied with checking "Do not ask again".???????????????????????????????????????????????????????????????????????????
        CameraPermissionHelper.launchPermissionSettings(this)
      }
      finish()
    }
  }

  // ?????????????????????????????????
  private fun checkAndRequestStoragePermission(): Boolean {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
      != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
        REQUEST_WRITE_EXTERNAL_STORAGE
      )
      return false
    }
    return true
  }

  //????????????
  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
  }

  // ????????????????????????
  enum class AppState {
    Idle, // ??????
    Recording, // ????????????
    Playingback // ????????????
  }

  // ??????????????????????????????
  var appState = AppState.Idle

  // ??????????????????
  private fun updateRecordButton() {
    val buttonView = findViewById<View>(R.id.record_button) as Button
    when (appState) {
      AppState.Idle -> {
        buttonView.text = getString(R.string.record)
        buttonView.visibility = View.VISIBLE
      }
      AppState.Recording -> {
        buttonView.text = getString(R.string.stop)
        buttonView.visibility = View.VISIBLE
      }
      AppState.Playingback ->
        buttonView.visibility = View.INVISIBLE
    }
  }

  // ??????????????????
  private fun updatePlaybackButton() {
    val button = findViewById<View>(R.id.playback_button) as Button
    when (appState) {
      AppState.Idle -> {
        button.text = getString(R.string.playback)
        button.visibility = View.VISIBLE
      }
      AppState.Playingback -> {
        button.text = getString(R.string.stop)
        button.visibility = View.VISIBLE
      }
      AppState.Recording ->
        button.visibility = View.INVISIBLE
    }
  }

  // ??????ARCore?????????GLSurfaceView??????
  private fun pauseARCoreSession() {
    // Pause the GLSurfaceView so that it doesn't update the ARCore session.
    // Pause the ARCore session so that we can update its configuration.
    // If the GLSurfaceView is not paused,
    //   onDrawFrame() will try to update the ARCore session
    //   while it's paused, resulting in a crash.
    view.surfaceView.onPause()
    arCoreSessionHelper.session?.pause()
  }

  // ??????ARCore??????
  private fun resumeARCoreSession(): Boolean {
    // We must resume the ARCore session before the GLSurfaceView.
    // Otherwise, the GLSurfaceView will try to update the ARCore session.
    try {
      arCoreSessionHelper.session?.resume()
    } catch (e: CameraNotAvailableException) {
      Log.e(TAG, "CameraNotAvailableException in resumeARCoreSession", e)
      return false
    }
    view.surfaceView.onResume()
    myLogMessage("----New Session------\n")
    return true
  }

  // ????????????
  private fun startRecording(): Boolean {
    val mp4FileUri: Uri = createMp4File() ?: return false
    val session = arCoreSessionHelper.session ?: return false
    Log.d(TAG, "startRecording at: $mp4FileUri")
    pauseARCoreSession() // ?????????ARCore??????
    val anchorTrack = Track(arCoreSessionHelper.session).setId(ANCHOR_TRACK_ID).setMimeType(
      ANCHOR_TRACK_MIME_TYPE)

    // Configure the ARCore session to start recording.
    val recordingConfig = RecordingConfig(session)
      .setMp4DatasetUri(mp4FileUri)
      .setAutoStopOnPause(true) //?????? ARCore ????????????????????????????????????
      .addTrack(anchorTrack) //??????Track?????????recordingConfig
    try {
      // Prepare the session for recording, but do not start recording yet.
      session.startRecording(recordingConfig)
    } catch (e: RecordingFailedException) {
      Log.e(TAG, "startRecording - Failed to prepare to start recording", e)
      textView.text = "??????????????????"
      return false
    }
    val canResume: Boolean = resumeARCoreSession()
    if (!canResume) return false
    // Correctness checking: check the ARCore session's RecordingState.
    val recordingStatus: RecordingStatus = session.recordingStatus
    Log.d(
      TAG,
      String.format("startRecording - recordingStatus %s", recordingStatus)
    )
    textView.text = "???????????????$recordingStatus"
    //Do not concatenate text displayed with setText. Use resource string with placeholders.
    return recordingStatus == RecordingStatus.OK
  }

  // ????????????
  private fun stopRecording(): Boolean {
    try {
      arCoreSessionHelper.session?.stopRecording()
    } catch (e: RecordingFailedException) {
      Log.e(TAG, "stopRecording - Failed to stop recording", e)
      textView.text="?????????????????????"
      return false
    }
    // Correctness checking: check if the session stopped recording.
    return arCoreSessionHelper.session?.recordingStatus === RecordingStatus.NONE
  }

  // ??????????????????
  fun onClickRecord(view: View) {
    Log.d(TAG, "onClickRecord")
    when (appState) {
      AppState.Idle -> {
        val hasStarted: Boolean = startRecording()
        Log.d(TAG, String.format("onClickRecord start: hasStarted %b", hasStarted))
        if (hasStarted) appState = AppState.Recording
      }
      AppState.Recording -> {
        val hasStopped: Boolean = stopRecording()
        Log.d(TAG, String.format("onClickRecord stop: hasStopped %b", hasStopped))
        if (hasStopped) {
          appState = AppState.Idle
          textView.text=""
          Toast.makeText(this, "????????????????????????/Movies", Toast.LENGTH_LONG).show()
        }
      }
      else -> {}
    }
    updateRecordButton()
    updatePlaybackButton()
  }

  // ???????????????????????????
  private fun selectFileToPlayback(): Boolean {
    // Start file selection from Movies directory.
    // Android 10 and above requires VOLUME_EXTERNAL_PRIMARY to write to MediaStore.
    val videoCollection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      MediaStore.Video.Media.getContentUri(
        MediaStore.VOLUME_EXTERNAL_PRIMARY
      )
    } else {
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    // Create an Intent to select a file.
    // ???????????? Intent ????????????????????????
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    // Add file filters such as the MIME type, the default directory and the file category.
    // ?????????????????????????????? MIME ???????????????????????????????????????
    intent.type = MP4_VIDEO_MIME_TYPE // Only select *.mp4 files ?????????MP4??????
    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, videoCollection) // Set default directory
    intent.addCategory(Intent.CATEGORY_OPENABLE) // Must be files that can be opened
    this.startActivityForResult(intent, REQUEST_MP4_SELECTOR)
    return true
  }

  // ????????????
  private fun startPlayingback(mp4FileUri: Uri?): Boolean {
    if (mp4FileUri == null) return false
    Log.d(TAG, "startPlayingback at:$mp4FileUri")
    pauseARCoreSession()
    try {
      arCoreSessionHelper.session?.setPlaybackDatasetUri(mp4FileUri)
    } catch (e: PlaybackFailedException) {
      Log.e(TAG, "startPlayingback - setPlaybackDataset failed", e)
    }
    // The session's camera texture name becomes invalid when the
    // ARCore session is set to play back.
    // Workaround: Reset the Texture to start Playback
    // so it doesn't crashes with AR_ERROR_TEXTURE_NOT_SET.
    // ??? ARCore ?????????????????????????????????????????????????????????????????????
    // ???????????????????????????????????????????????????????????? AR_ERROR_TEXTURE_NOT_SET ????????????
    renderer.hasSetTextureNames = false
    val canResume = resumeARCoreSession()
    if (!canResume) return false
    val playbackStatus: PlaybackStatus? = arCoreSessionHelper.session?.playbackStatus
    Log.d(
      TAG,
      String.format("startPlayingback - playbackStatus %s", playbackStatus)
    )
    textView.text="???????????????$playbackStatus"
    if (playbackStatus != PlaybackStatus.OK) { // Correctness check
      return false
    }
    appState = AppState.Playingback
    updateRecordButton()
    updatePlaybackButton()
    return true
  }

  // ????????????
  fun stopPlayback(): Boolean {
    // Correctness check, only stop playing back when the app is playing back.
    if (appState !== AppState.Playingback) return false
    pauseARCoreSession()
    // Close the current session and create a new session.
    // ?????????????????????????????????????????????
    arCoreSessionHelper.session?.close()
    try {
      arCoreSessionHelper.session = Session(this)
    } catch (e: UnavailableArcoreNotInstalledException) {
      Log.e(TAG, "Error in return to Idle state. Cannot create new ARCore session. Arcore NotInstalled.", e)
      return false
    } catch (e: UnavailableApkTooOldException) {
      Log.e(TAG, "Error in return to Idle state. Cannot create new ARCore session. Please update ARCore", e)
      return false
    } catch (e: UnavailableSdkTooOldException) {
      Log.e(TAG, "Error in return to Idle state. Cannot create new ARCore session. Please update this App", e)
      return false
    } catch (e: UnavailableDeviceNotCompatibleException) {
      Log.e(TAG, "Error in return to Idle state. Cannot create new ARCore session. Device Not Compatible", e)
      return false
    }
    configureSession(arCoreSessionHelper.session!!)
    val canResume = resumeARCoreSession()
    if (!canResume) return false
    // A new session will not have a camera texture name.
    // Manually set hasSetTextureNames to false to trigger a reset.
    renderer.hasSetTextureNames = false
    // Reset appState to Idle, and update the "Record" and "Playback" buttons.
    appState = AppState.Idle
    updateRecordButton()
    updatePlaybackButton()
    return true
  }

  // ??????ActivityResult ????????????MP4??????
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    // Check request status. Log an error if the selection fails.
    // ????????????????????????????????????????????????????????????
    if (resultCode != RESULT_OK || requestCode != REQUEST_MP4_SELECTOR) {
      Log.e(TAG, "onActivityResult select file failed")
      return
    }
    val mp4FileUri = data?.data
    Log.d(TAG, String.format("onActivityResult result is %s", mp4FileUri))
    // Begin playback.
    startPlayingback(mp4FileUri)
  }

  // ??????????????????
  fun onClickPlayback(view: View) {
    Log.d(TAG, "onClickPlayback")
    when (appState) {
      AppState.Idle -> {
        val hasStarted: Boolean = selectFileToPlayback()
        Log.d(TAG, String.format("onClickPlayback start: selectFileToPlayback %b", hasStarted))
      }
      AppState.Playingback -> {
        val hasStopped: Boolean = stopPlayback()
        Log.d(TAG, String.format("onClickPlayback stop: hasStopped %b", hasStopped))
        textView.text=""
      }
      else -> {}
    }
    // Update the UI for the "Record" and "Playback" buttons.
    updateRecordButton()
    updatePlaybackButton()
  }

  //??????MP4?????????
  private fun createMp4File(): Uri? {
    // Since we use legacy external storage for Android 10,
    // we still need to request for storage permission on Android 10.
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
      if (!checkAndRequestStoragePermission()) {
        Log.i(TAG, String.format(
          "Didn't createMp4File. No storage permission, API Level = %d",
          Build.VERSION.SDK_INT))
        return null
      }
    }
    val dateFormat = SimpleDateFormat(FILE_TIME_PATTERN, Locale.PRC)
    val mp4FileName = "arcore-" + dateFormat.format(Date()).toString() + ".mp4"
    val resolver = this.contentResolver
    val videoCollection: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      MediaStore.Video.Media.getContentUri(
        MediaStore.VOLUME_EXTERNAL_PRIMARY
      )
    } else {
      MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    // Create a new Media file record.
    val newMp4FileDetails = ContentValues()
    newMp4FileDetails.put(MediaStore.Video.Media.DISPLAY_NAME, mp4FileName)
    newMp4FileDetails.put(MediaStore.Video.Media.MIME_TYPE, MP4_VIDEO_MIME_TYPE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // The Relative_Path column is only available since API Level 29.
      newMp4FileDetails.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
    } else {
      // Use the Data column to set path for API Level <= 28.
      val mp4FileDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
      val absoluteMp4FilePath = File(mp4FileDir, mp4FileName).absolutePath
      newMp4FileDetails.put(MediaStore.Video.Media.DATA, absoluteMp4FilePath)
    }
    val newMp4FileUri = resolver.insert(videoCollection, newMp4FileDetails)
    // Ensure that this file exists and can be written.
    if (newMp4FileUri == null) {
      Log.e(TAG, String.format("Failed to insert Video entity in MediaStore. API Level = %d", Build.VERSION.SDK_INT))
      return null
    }
    // This call ensures the file exist before we pass it to the ARCore API.
    if (!testFileWriteAccess(newMp4FileUri)) {
      return null
    }
    Log.d(TAG, String.format("createMp4File = %s, API Level = %d", newMp4FileUri, Build.VERSION.SDK_INT))
    return newMp4FileUri
  }

  // ???????????? Uri ??????????????????????????????????????????????????????
  private fun testFileWriteAccess(contentUri: Uri): Boolean {
    try {
      this.contentResolver.openOutputStream(contentUri).use {
        Log.d(TAG, String.format("Success in testFileWriteAccess %s", contentUri.toString()))
        return true
      }
    } catch (e: FileNotFoundException) {
      Log.e(TAG, String.format("FileNotFoundException in testFileWriteAccess %s", contentUri.toString()), e)
    } catch (e: IOException) {
      Log.e(TAG, String.format("IOException in testFileWriteAccess %s", contentUri.toString()), e)
    }
    return false
  }
}


