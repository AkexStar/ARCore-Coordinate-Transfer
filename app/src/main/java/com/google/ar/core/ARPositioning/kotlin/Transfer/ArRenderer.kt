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

import android.opengl.GLES30
import android.opengl.Matrix
import android.os.Message
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.*
import com.google.ar.core.ARPositioning.java.common.helpers.DisplayRotationHelper
import com.google.ar.core.ARPositioning.java.common.helpers.TrackingStateHelper
import com.google.ar.core.ARPositioning.java.common.samplerender.Framebuffer
import com.google.ar.core.ARPositioning.java.common.samplerender.GLError
import com.google.ar.core.ARPositioning.java.common.samplerender.Mesh
import com.google.ar.core.ARPositioning.java.common.samplerender.SampleRender
import com.google.ar.core.ARPositioning.java.common.samplerender.Shader
import com.google.ar.core.ARPositioning.java.common.samplerender.Texture
import com.google.ar.core.ARPositioning.java.common.samplerender.VertexBuffer
import com.google.ar.core.ARPositioning.java.common.samplerender.arcore.BackgroundRenderer
import com.google.ar.core.ARPositioning.java.common.samplerender.arcore.PlaneRenderer
import com.google.ar.core.ARPositioning.java.common.samplerender.arcore.SpecularCubemapFilter
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import java.io.IOException
import java.nio.ByteBuffer

/** Renders the HelloAR application using our example Renderer. */
class HelloArRenderer(val activity: MainActivity) :
  SampleRender.Renderer, DefaultLifecycleObserver {
  companion object {
    val TAG = "HelloArRenderer"

    // See the definition of updateSphericalHarmonicsCoefficients for an explanation of these
    // constants. 查看 更新球谐系数 来获取以下内容的解释
    private val sphericalHarmonicFactors = // 球协因子系数？
      floatArrayOf(
        0.282095f,
        -0.325735f,
        0.325735f,
        -0.325735f,
        0.273137f,
        -0.273137f,
        0.078848f,
        -0.273137f,
        0.136569f
      )

    private val Z_NEAR = 0.1f
    private val Z_FAR = 80f //100

    // Assumed distance from the device camera to the surface on which user will try to place
    // objects. 从设备相机到用户将尝试放置对象的表面的假定距离。
    // This value affects the apparent scale of objects while the tracking method of the
    // Instant Placement point is SCREENSPACE_WITH_APPROXIMATE_DISTANCE.
    // 此值会影响物体的表观比例，而 Instant Placement 点的跟踪方法是 SCREENSPACE_WITH_APPROXIMATE_DISTANCE。
    // Values in the [0.2, 2.0] meter range are a good choice for most AR experiences. Use lower
    // values for AR experiences where users are expected to place objects on surfaces close to the
    // camera. Use larger values for experiences where the user will likely be standing and trying
    // to place an object on the ground or floor in front of them.
    // [0.2, 2.0] 米范围内的值是大多数 AR 体验的不错选择。对 AR 体验使用较低的值，用户需要将对象放置在靠近相机的表面上。
    // 对于用户可能站立并试图将物体放在他们面前的地面或地板上的体验，请使用较大的值。
    val APPROXIMATE_DISTANCE_METERS = 1.0f //2

    val CUBEMAP_RESOLUTION = 16
    val CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES = 64 //32
  }
  var dataStr = ""
  lateinit var render: SampleRender
  lateinit var planeRenderer: PlaneRenderer
  lateinit var backgroundRenderer: BackgroundRenderer
  lateinit var virtualSceneFramebuffer: Framebuffer
  var hasSetTextureNames = false

  // Point Cloud 点云
  lateinit var pointCloudVertexBuffer: VertexBuffer
  lateinit var pointCloudMesh: Mesh
  lateinit var pointCloudShader: Shader

  // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
  // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
  // 跟踪最后渲染的点云，以避免在未更改点云时更新 VBO。使用时间戳执行此操作，因为我们无法比较 PointCloud 对象。
  var lastPointCloudTimestamp: Long = 0

  // Virtual object (ARCore pawn) 虚拟物体的变量
  lateinit var virtualObjectMesh: Mesh
  lateinit var virtualObjectShader: Shader
  lateinit var virtualObjectAlbedoTexture: Texture
  lateinit var virtualObjectAlbedoInstantPlacementTexture: Texture

  // Anchor列表
  private val wrappedAnchors = mutableListOf<WrappedAnchor>()

  // Environmental HDR
  lateinit var dfgTexture: Texture
  lateinit var cubemapFilter: SpecularCubemapFilter

  // Temporary matrix allocated here to reduce number of allocations for each frame.
  // 此处分配的临时矩阵以减少每帧的分配次数。
  val modelMatrix = FloatArray(16)
  val viewMatrix = FloatArray(16)
  val projectionMatrix = FloatArray(16)
  val modelViewMatrix = FloatArray(16) // view x model

  val modelViewProjectionMatrix = FloatArray(16) // projection x view x model

  val sphericalHarmonicsCoefficients = FloatArray(9 * 3)
  val viewInverseMatrix = FloatArray(16)
  val worldLightDirection = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f)
  val viewLightDirection = FloatArray(4) // view x world light direction


  val session
    get() = activity.arCoreSessionHelper.session

  val displayRotationHelper = DisplayRotationHelper(activity)
  val trackingStateHelper = TrackingStateHelper(activity)

  override fun onResume(owner: LifecycleOwner) {
    displayRotationHelper.onResume()
    hasSetTextureNames = false
  }

  override fun onPause(owner: LifecycleOwner) {
    displayRotationHelper.onPause()
  }

  override fun onSurfaceCreated(render: SampleRender) {
    // Prepare the rendering objects. 准备渲染对象
    // This involves reading shaders and 3D model files, so may throw an IOException.
    // 这涉及读取着色器和 3D 模型文件，因此可能会引发 IOException。
    activity.mHandler.sendEmptyMessage(0)
    try {
      planeRenderer = PlaneRenderer(render)
      backgroundRenderer = BackgroundRenderer(render)
      virtualSceneFramebuffer = Framebuffer(render, /*width=*/ 1, /*height=*/ 1)

      cubemapFilter =
        SpecularCubemapFilter(render, CUBEMAP_RESOLUTION, CUBEMAP_NUMBER_OF_IMPORTANCE_SAMPLES)
      // Load environmental lighting values lookup table 加载环境照明值查找表
      dfgTexture =
        Texture(
          render,
          Texture.Target.TEXTURE_2D,
          Texture.WrapMode.CLAMP_TO_EDGE,
          /*useMipmaps=*/ false
        )
      // The dfg.raw file is a raw half-float texture with two channels.
      // dfg.raw 文件是具有两个通道的原始半浮点纹理图
      val dfgResolution = 64
      val dfgChannels = 2
      val halfFloatSize = 2

      val buffer: ByteBuffer =
        ByteBuffer.allocateDirect(dfgResolution * dfgResolution * dfgChannels * halfFloatSize)
      activity.assets.open("models/dfg.raw").use { it.read(buffer.array()) }

      // SampleRender abstraction leaks here.
      // 生成背景网格纹理
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dfgTexture.textureId)
      GLError.maybeThrowGLException("Failed to bind DFG texture", "glBindTexture")
      GLES30.glTexImage2D(
        GLES30.GL_TEXTURE_2D,
        /*level=*/ 0,
        GLES30.GL_RG16F,
        /*width=*/ dfgResolution,
        /*height=*/ dfgResolution,
        /*border=*/ 0,
        GLES30.GL_RG,
        GLES30.GL_HALF_FLOAT,
        buffer
      )
      GLError.maybeThrowGLException("Failed to populate DFG texture", "glTexImage2D")

      // Point cloud 点云着色设置
      pointCloudShader =
        Shader.createFromAssets(
            render,
            "shaders/point_cloud.vert",
            "shaders/point_cloud.frag",
            /*defines=*/ null
          )
          .setVec4("u_Color", floatArrayOf(31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f))
          .setFloat("u_PointSize", 3.0f)

      // four entries per vertex: X, Y, Z, confidence
      pointCloudVertexBuffer =
        VertexBuffer(render, /*numberOfEntriesPerVertex=*/ 4, /*entries=*/ null)
      val pointCloudVertexBuffers = arrayOf(pointCloudVertexBuffer)
      pointCloudMesh =
        Mesh(render, Mesh.PrimitiveMode.POINTS, /*indexBuffer=*/ null, pointCloudVertexBuffers)

      // Virtual object to render (ARCore pawn)
      virtualObjectAlbedoTexture =
        Texture.createFromAsset(
          render,
          "models/pawn_albedo.png",
          Texture.WrapMode.CLAMP_TO_EDGE,
          Texture.ColorFormat.SRGB
        )

      virtualObjectAlbedoInstantPlacementTexture =
        Texture.createFromAsset(
          render,
          "models/pawn_albedo_instant_placement.png",
          Texture.WrapMode.CLAMP_TO_EDGE,
          Texture.ColorFormat.SRGB
        )

      val virtualObjectPbrTexture =
        Texture.createFromAsset(
          render,
          "models/pawn_roughness_metallic_ao.png",
          Texture.WrapMode.CLAMP_TO_EDGE,
          Texture.ColorFormat.LINEAR
        )
      virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj")
      virtualObjectShader =
        Shader.createFromAssets(
            render,
            "shaders/environmental_hdr.vert",
            "shaders/environmental_hdr.frag",
            mapOf("NUMBER_OF_MIPMAP_LEVELS" to cubemapFilter.numberOfMipmapLevels.toString())
          )
          .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
          .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture)
          .setTexture("u_Cubemap", cubemapFilter.filteredCubemapTexture)
          .setTexture("u_DfgTexture", dfgTexture)
    } catch (e: IOException) {
      Log.e(TAG, "Failed to read a required asset file", e)
      showMessageError("未能读取所需的资源文件: $e") //Failed to read a required asset file
    }
  }

  override fun onSurfaceChanged(render: SampleRender, width: Int, height: Int) {
    displayRotationHelper.onSurfaceChanged(width, height)
    virtualSceneFramebuffer.resize(width, height)
  }


  override fun onDrawFrame(render: SampleRender) {
    val session = session ?: return
    // Texture names should only be set once on a GL thread unless they change. This is done during
    // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
    // initialized during the execution of onSurfaceCreated.
    // 纹理名称只能在 GL 线程上设置一次，除非它们更改。这是在 onDrawFrame 而不是 onSurfaceCreated 期间完成的，
    // 因为不能保证会话在 onSurfaceCreated 执行期间已被初始化。
    if (!hasSetTextureNames) {
      session.setCameraTextureNames(intArrayOf(backgroundRenderer.cameraColorTexture.textureId))
      hasSetTextureNames = true
    }
    // -- Update per-frame state 更新每帧状态
    // Notify ARCore session that the view size changed so that the perspective matrix and
    // the video background can be properly adjusted.
    // 通知 ARCore 会话视图大小已更改，以便可以正确调整透视矩阵和视频背景。
    displayRotationHelper.updateSessionIfNeeded(session)
    // Obtain the current frame from ARSession. When the configuration is set to
    // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
    // camera framerate.
    // 从 ARSession 获取当前帧。当配置设置为 UpdateMode.BLOCKING（默认情况下）时，这会将渲染限制为相机帧速率。
    val frame =
      try {
        session.update()
      } catch (e: CameraNotAvailableException) {
        Log.e(TAG, "Camera not available during onDrawFrame", e)
        showMessageError("相机不可用,请重启App") //Camera not available. Try restarting the app.
        return
      }
    if (activity.appState == MainActivity.AppState.Playingback && // 停止播放就不再更新Frame
            activity.arCoreSessionHelper.session?.playbackStatus == PlaybackStatus.FINISHED){
              activity.runOnUiThread{activity.stopPlayingback()}
              return
    }
    val camera = frame.camera
    // Update BackgroundRenderer state to match the depth settings.
    // 更新 BackgroundRenderer 状态以匹配深度设置。
    try {
      backgroundRenderer.setUseDepthVisualization(
        render,
        activity.depthSettings.depthColorVisualizationEnabled()
      )
      backgroundRenderer.setUseOcclusion(render, activity.depthSettings.useDepthForOcclusion())
    } catch (e: IOException) {
      Log.e(TAG, "Failed to read a required asset file", e)
      showMessageError("未能读取所需的资源文件: $e") // Failed to read a required asset file
      return
    }
    // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
    // used to draw the background camera image.
    // 必须每帧调用 BackgroundRenderer.updateDisplayGeometry 以更新用于绘制背景摄像机图像的坐标。
    backgroundRenderer.updateDisplayGeometry(frame)
    val shouldGetDepthImage =
      activity.depthSettings.useDepthForOcclusion() ||
        activity.depthSettings.depthColorVisualizationEnabled()
    if (camera.trackingState == TrackingState.TRACKING && shouldGetDepthImage) {
      try {
        val depthImage = frame.acquireDepthImage()
        backgroundRenderer.updateCameraDepthTexture(depthImage)
        depthImage.close()
      } catch (e: NotYetAvailableException) {
        // This normally means that depth data is not available yet. This is normal so we will not
        // spam the logcat with this.
        // 这通常意味着深度数据尚不可用。这是正常的，所以我们不会向 logcat 发送垃圾邮件。
      }
    }
    // Handle one tap per frame.每帧处理一次屏幕点击。
    handleTap(frame, camera)
    // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
    // 在跟踪时保持屏幕解锁，但在跟踪停止时让它锁定。
    trackingStateHelper.updateKeepScreenOnFlag(camera.trackingState)
    // Show a message based on whether tracking has failed, if planes are detected, and if the user
    // has placed any objects.
    // 根据跟踪是否失败、是否检测到平面以及用户是否放置了任何对象，来显示消息。
    val message: String? =
      when {
        camera.trackingState == TrackingState.PAUSED &&
          camera.trackingFailureReason == TrackingFailureReason.NONE ->
          activity.getString(R.string.searching_planes)
        camera.trackingState == TrackingState.PAUSED ->
          TrackingStateHelper.getTrackingFailureReasonString(camera)
        session.hasTrackingPlane() && wrappedAnchors.isEmpty() ->
          activity.getString(R.string.waiting_taps)
        session.hasTrackingPlane() && wrappedAnchors.isNotEmpty() -> null
        else -> activity.getString(R.string.searching_planes)
      }
    if (message == null) {
      activity.view.snackbarHelper.hide(activity)
    } else {
      showMessage(message)
    }
    // -- 绘制背景Draw background
    if (frame.timestamp != 0L) {
      // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
      // drawing possible leftover data from previous sessions if the texture is reused.
      // 如果相机尚未生成第一帧，则禁止渲染。这是为了避免在重复使用纹理时从以前的会话中绘制可能的剩余数据。
      backgroundRenderer.drawBackground(render)
    }
    // If not tracking, don't draw 3D objects. 如果没有在跟踪，则不要绘制 3D 对象。
    if (camera.trackingState == TrackingState.PAUSED) {
      return
    }
    // -- Draw non-occluded virtual objects (planes, point cloud)
    // 绘制非遮挡的虚拟对象（平面、点云）

    // Get projection matrix. 获取投影矩阵。
    camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR)

    // Get camera matrix and draw. 获取相机矩阵并绘制。
    camera.getViewMatrix(viewMatrix, 0)
    frame.acquirePointCloud().use { pointCloud ->
      if (pointCloud.timestamp > lastPointCloudTimestamp) {
        pointCloudVertexBuffer.set(pointCloud.points)
        lastPointCloudTimestamp = pointCloud.timestamp
      }
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
      pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
      render.draw(pointCloudMesh, pointCloudShader)
    }
    // Visualize planes.
    planeRenderer.drawPlanes(
      render,
      session.getAllTrackables<Plane>(Plane::class.java),
      camera.displayOrientedPose,
      projectionMatrix
    )
    dataStr = String.format("Camera x=%.3f y=%.3f z=%.3f\n",camera.pose.tx(), camera.pose.ty(), camera.pose.tz())
//    activity.textView.text = str
//    activity.textView.text = camera.pose.tx().toString() + " " + camera.pose.ty().toString() + " " + camera.pose.tz().toString()
    // -- Draw occluded virtual objects

    // Update lighting parameters in the shader
    updateLightEstimation(frame.lightEstimate, viewMatrix)

    // Visualize anchors created by touch.
    render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f)
    for ((anchor, trackable) in
      wrappedAnchors.filter { it.anchor.trackingState == TrackingState.TRACKING }) {
      // Get the current pose of an Anchor in world space. The Anchor pose is updated
      // during calls to session.update() as ARCore refines its estimate of the world.
        // 获取 Anchor 在世界空间中的当前姿势。 Anchor 姿势在调用 session.update() 期间更新，因为 ARCore 改进了它对世界的估计。
      anchor.pose.toMatrix(modelMatrix, 0)
      dataStr += String.format("x=%.3f y=%.3f z=%.3f\n", anchor.pose.tx(), anchor.pose.ty(), anchor.pose.tz())
      // Calculate model/view/projection matrices
      Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
      Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

      // Update shader properties and draw
      virtualObjectShader.setMat4("u_ModelView", modelViewMatrix)
      virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix)
      val texture =
        if ((trackable as? InstantPlacementPoint)?.trackingMethod ==
            InstantPlacementPoint.TrackingMethod.SCREENSPACE_WITH_APPROXIMATE_DISTANCE
        ) {
          virtualObjectAlbedoInstantPlacementTexture
        } else {
          virtualObjectAlbedoTexture
        }
      virtualObjectShader.setTexture("u_AlbedoTexture", texture)
      render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer)
    }
    Thread{
      activity.mHandler.sendMessage(Message.obtain().apply {
        what = 1
        obj = dataStr
      })
    }.start()
    // Compose the virtual scene with the background.
    backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR)
  }

  /** Checks if we detected at least one plane. */
  private fun Session.hasTrackingPlane() =
    getAllTrackables(Plane::class.java).any { it.trackingState == TrackingState.TRACKING }

  /** Update state based on the current frame's light estimation. */
  private fun updateLightEstimation(lightEstimate: LightEstimate, viewMatrix: FloatArray) {
    if (lightEstimate.state != LightEstimate.State.VALID) {
      virtualObjectShader.setBool("u_LightEstimateIsValid", false)
      return
    }
    virtualObjectShader.setBool("u_LightEstimateIsValid", true)
    Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0)
    virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix)
    updateMainLight(
      lightEstimate.environmentalHdrMainLightDirection,
      lightEstimate.environmentalHdrMainLightIntensity,
      viewMatrix
    )
    updateSphericalHarmonicsCoefficients(lightEstimate.environmentalHdrAmbientSphericalHarmonics)
    cubemapFilter.update(lightEstimate.acquireEnvironmentalHdrCubeMap())
  }

  private fun updateMainLight(
    direction: FloatArray,
    intensity: FloatArray,
    viewMatrix: FloatArray
  ) {
    // We need the direction in a vec4 with 0.0 as the final component to transform it to view space
    worldLightDirection[0] = direction[0]
    worldLightDirection[1] = direction[1]
    worldLightDirection[2] = direction[2]
    Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0)
    virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection)
    virtualObjectShader.setVec3("u_LightIntensity", intensity)
  }

  private fun updateSphericalHarmonicsCoefficients(coefficients: FloatArray) {
    // Pre-multiply the spherical harmonics coefficients before passing them to the shader. The
    // constants in sphericalHarmonicFactors were derived from three terms:
    //
    // 1. The normalized spherical harmonics basis functions (y_lm)
    //
    // 2. The lambertian diffuse BRDF factor (1/pi)
    //
    // 3. A <cos> convolution. This is done to so that the resulting function outputs the irradiance
    // of all incoming light over a hemisphere for a given surface normal, which is what the shader
    // (environmental_hdr.frag) expects.
    //
    // You can read more details about the math here:
    // https://google.github.io/filament/Filament.html#annex/sphericalharmonics
    require(coefficients.size == 9 * 3) {
      "The given coefficients array must be of length 27 (3 components per 9 coefficients"
    }

    // Apply each factor to every component of each coefficient
    for (i in 0 until 9 * 3) {
      sphericalHarmonicsCoefficients[i] = coefficients[i] * sphericalHarmonicFactors[i / 3]
    }
    virtualObjectShader.setVec3Array(
      "u_SphericalHarmonicsCoefficients",
      sphericalHarmonicsCoefficients
    )
  }

  // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
  private fun handleTap(frame: Frame, camera: Camera) {
    if (camera.trackingState != TrackingState.TRACKING) return
    val tap = activity.view.tapHelper.poll() ?: return

    val hitResultList =
      if (activity.instantPlacementSettings.isInstantPlacementEnabled) {
        frame.hitTestInstantPlacement(tap.x, tap.y, APPROXIMATE_DISTANCE_METERS)
      } else {
        frame.hitTest(tap)
      }

    // Hits are sorted by depth. Consider only closest hit on a plane, Oriented Point, Depth Point,
    // or Instant Placement Point.
    val firstHitResult =
      hitResultList.firstOrNull { hit ->
        when (val trackable = hit.trackable!!) {
          is Plane ->
            trackable.isPoseInPolygon(hit.hitPose) &&
              PlaneRenderer.calculateDistanceToPlane(hit.hitPose, camera.pose) > 0
          is Point -> trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
          is InstantPlacementPoint -> true
          // DepthPoints are only returned if Config.DepthMode is set to AUTOMATIC.
          is DepthPoint -> true
          else -> false
        }
      }

    if (firstHitResult != null) {
      // Cap the number of objects created. This avoids overloading both the
      // rendering system and ARCore.
      if (wrappedAnchors.size >= 20) {
        wrappedAnchors[0].anchor.detach()
        wrappedAnchors.removeAt(0)
      }

      // Adding an Anchor tells ARCore that it should track this position in
      // space. This anchor is created on the Plane to place the 3D model
      // in the correct position relative both to the world and to the plane.
      wrappedAnchors.add(WrappedAnchor(firstHitResult.createAnchor(), firstHitResult.trackable))

      // For devices that support the Depth API, shows a dialog to suggest enabling
      // depth-based occlusion. This dialog needs to be spawned on the UI thread.
      activity.runOnUiThread { activity.view.showOcclusionDialogIfNeeded() }
    }
  }

  private fun showMessageError(errorMessage: String) =
    activity.view.snackbarHelper.showError(activity, errorMessage)
  private fun showMessage(Message: String) =
    activity.view.snackbarHelper.showMessage(activity, Message)
}


/**
 * Associates an Anchor with the trackable it was attached to. This is used to be able to check
 * whether or not an Anchor originally was attached to an {@link InstantPlacementPoint}.
 * 将 Anchor 与它所附加的 trackable 相关联。这用于检查 Anchor 是否最初附加到 {@link InstantPlacementPoint}。
 */
private data class WrappedAnchor(
  val anchor: Anchor,
  val trackable: Trackable,
)
