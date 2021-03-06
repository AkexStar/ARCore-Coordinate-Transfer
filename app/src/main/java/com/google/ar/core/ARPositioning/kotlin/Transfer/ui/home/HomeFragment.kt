package com.google.ar.core.ARPositioning.kotlin.Transfer.ui.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.CustomMapStyleOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.services.core.ServiceSettings
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.ar.core.ARPositioning.kotlin.Transfer.GPSUtil
import com.google.ar.core.ARPositioning.kotlin.Transfer.databinding.FragmentHomeBinding
import com.google.ar.core.ARPositioning.kotlin.Transfer.CoordinateTransform
import com.google.ar.core.ARPositioning.kotlin.Transfer.ui.DataViewActivity
import com.permissionx.guolindev.PermissionX
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.exitProcess


class HomeFragment : Fragment() {
    companion object{
        const val FILE_TIME_PATTERN = "MMdd-HHmmss"
        const val TAG = "HomeFragment"
    }

    private var _binding: FragmentHomeBinding? = null
    private var haveLocationPermission = false

    lateinit var aMap: AMap

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    var projectName: String = ""
    var projectTime: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        val textView: TextView = binding.textHome
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }
        binding.map.onCreate(savedInstanceState)
        binding.buttonSetProject.setOnClickListener {
            val str = binding.editText.text.toString()
            if (str == ""){
                Toast.makeText(activity, "请输入项目名称！", Toast.LENGTH_SHORT).show()
            }else {
                val dateFormat = SimpleDateFormat(FILE_TIME_PATTERN, Locale.PRC).format(
                    Date()
                ).toString()
                activity?.getSharedPreferences("PROJECT",Context.MODE_PRIVATE)?.edit{
                    putString("name", str)
                }
                projectName = str
                activity?.getSharedPreferences("PROJECT",Context.MODE_PRIVATE)?.edit{
                    putString("time", dateFormat)
                }
                projectTime = dateFormat
                Toast.makeText(activity, "工程已建立！", Toast.LENGTH_SHORT).show()
                binding.textHome.text = "当前工程：$str，创建时间：$dateFormat"
            }
        }
        binding.buttonDataView.setOnClickListener {
            val intent = Intent(activity, DataViewActivity::class.java)
            startActivity(intent)
        }
        initPermission()
        ServiceSettings.updatePrivacyShow(context, true, true);
        ServiceSettings.updatePrivacyAgree(context,true);
        aMap = binding.map.map
        aMap.uiSettings.isZoomControlsEnabled = true
        aMap.setCustomMapStyle(
            CustomMapStyleOptions()
                .setEnable(true)
                .setStyleDataPath("/assets/amap/style.data")
                .setStyleExtraPath("/assets/amap/style_extra.data")
        )
        aMap.uiSettings.isMyLocationButtonEnabled = true;//设置默认定位按钮是否显示
        val myLocationStyle: MyLocationStyle = MyLocationStyle() //初始化定位蓝点样式类
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE)
        myLocationStyle.interval(2000) //设置连续定位模式下的定位间隔，只在连续定位模式下生效，单次定位模式下不会生效。单位为毫秒。
        aMap.myLocationStyle = myLocationStyle //设置定位蓝点的Style
        aMap.moveCamera(CameraUpdateFactory.zoomTo(16F))
        aMap.isMyLocationEnabled = true // 设置为true表示启动显示定位蓝点，false表示隐藏定位蓝点并不进行定位，默认是false。
        return root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
//        binding.map.onSaveInstanceState(outState)

    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.map.onDestroy()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        if (projectName == ""){
            Toast.makeText(activity, "请输入项目名称！", Toast.LENGTH_SHORT).show()
        }else {
            binding.textHome.text = "当前工程：$projectName，创建时间：$projectTime"
        }
        getLocationService()
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
    }
    //权限
    private fun initPermission() {
        PermissionX.init(activity)
            .permissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            .onExplainRequestReason { scope, deniedList ->
                val message = "APP需要您同意以下权限才能正常使用"
                scope.showRequestReasonDialog(deniedList, message, "允许", "取消")
            }
            .onForwardToSettings { scope, deniedList ->
                scope.showForwardToSettingsDialog(deniedList, "您需要去应用程序设置当中手动开启权限", "去设置", "退出")
            }
            .request { allGranted, _, deniedList ->
                if (allGranted) {
                    haveLocationPermission = true
                } else {
                    haveLocationPermission = false
                    Toast.makeText(context, "您拒绝了如下权限：$deniedList", Toast.LENGTH_SHORT).show()
                    exitProcess(0)
                }
            }
    }

    private fun SHA1(context: Context): String? {
        try {
            val info = context.packageManager.getPackageInfo(
                context.packageName, PackageManager.GET_SIGNATURES
            )
            val cert = info.signatures[0].toByteArray()
            val md = MessageDigest.getInstance("SHA1")
            val publicKey = md.digest(cert)
            val hexString = StringBuffer()
            for (i in publicKey.indices) {
                val appendString = Integer.toHexString(
                    0xFF and publicKey[i].toInt()).uppercase(Locale.US)
                if (appendString.length == 1) hexString.append("0")
                hexString.append(appendString)
                hexString.append(":")
            }
            val result: String = hexString.toString()
            return result.substring(0, result.length - 1)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        } catch (e: NoSuchAlgorithmException) {
            e.printStackTrace()
        }
        return null
    }
    private fun getLocationService() {
        if (haveLocationPermission) {
            //判断是否开启服务getSaveResponse
            val locationManager = activity?.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Log.e(TAG, "用户打开定位服务")
                getLocation()
            } else {
                Log.e(TAG, "用户关闭定位服务")
                context?.let {
                    MaterialAlertDialogBuilder(it)
                        .setMessage("请打开位置服务开关，否则可能无法正常使用")
                        .setCancelable(false)
                        .setPositiveButton("去打开") { _, _ ->
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            //startActivityForResult(intent, 1)
                            startActivity(intent)
                        }
                        .setNegativeButton("退出") { _, _ -> exitProcess(0) }
                        .show()
                }
            }
        }
    }

    private fun getLocation() {
        AMapLocationClient.updatePrivacyShow(context, true, true)
        AMapLocationClient.updatePrivacyAgree(context, true)
        //声明AMapLocationClient类对象
        val mLocationClient = AMapLocationClient(context)
        //声明定位回调监听器
        //val mLocationListener = AMapLocationListener() {}
        val mLocationOption = AMapLocationClientOption()
        mLocationOption.locationPurpose = AMapLocationClientOption.AMapLocationPurpose.SignIn
        mLocationClient.setLocationOption(mLocationOption)
        //设置场景模式后最好调用一次stop，再调用start以保证场景模式生效
        mLocationClient.stopLocation()
        mLocationClient.startLocation()
        //设置定位模式为AMapLocationMode.Hight_Accuracy，高精度模式。
        mLocationOption.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        //设置是否返回地址信息（默认返回地址信息）
        mLocationOption.isNeedAddress = true
        //设置是否允许模拟位置,默认为true，允许模拟位置
        mLocationOption.isMockEnable = true
        //单位是毫秒，默认30000毫秒，建议超时时间不要低于8000毫秒。
        mLocationOption.httpTimeOut = 20000
        mLocationOption.isSensorEnable = true
        mLocationOption.interval = 1000
        mLocationOption.isWifiScan =true
        //关闭缓存机制
        mLocationOption.isLocationCacheEnable = true
        //给定位客户端对象设置定位参数
        mLocationClient.setLocationOption(mLocationOption)
        //启动定位
        mLocationClient.startLocation()
        //设置定位回调监听
        mLocationClient.setLocationListener(MyLocationListener())
    }

    inner class MyLocationListener : AMapLocationListener {
        override fun onLocationChanged(p0: AMapLocation) {
            when (p0.errorCode) {
                0 -> {
                    var locationStr = ""// p0.city + p0.district + p0.street + "\n"
                    val h = p0.altitude
                    val a = p0.accuracy
                    val data0 = CoordinateTransform.transformGCJ02ToWGS84(p0.longitude, p0.latitude)
                    val lon = GPSUtil.convertToDegrees(data0[0].toString())
                    val lat = GPSUtil.convertToDegrees(data0[1].toString())
                    locationStr += "${lon[0]}°${lon[1]}'${lon[2]}\"E ${lat[0]}°${lat[1]}'${lat[2]}\"N ${h}m\nAccuracy: ${a}m"
                    binding.textViewLatLon.text = locationStr
                    Log.d(TAG, locationStr)
                    Log.d(TAG, p0.bearing.toString())
                    Log.d(TAG, p0.gpsAccuracyStatus.toString())
                    Log.d(TAG, p0.aoiName)
                }
                else -> {
                    val errorStr = "ErrCode:" + p0.errorCode + ", errInfo:" + p0.errorInfo
                    Log.e(TAG, "location Error, $errorStr")
                    binding.textViewLatLon.text = errorStr
                }
            }
        }
    }
}