package com.google.ar.core.ARPositioning.kotlin

import android.util.Log
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener

//fun updatePrivacyShow(context: Context?, isContains: Boolean, isShow: Boolean) {}
//var mAMapLocationListener: AMapLocationListener? = AMapLocationListener {
//
//}
//
//AMapLocationClient.updatePrivacyShow(context,true,true)
//AMapLocationClient.updatePrivacyAgree(context,true)
////声明AMapLocationClient类对象
//val mLocationClient: AMapLocationClient = AMapLocationClient(context)
////初始化定位
////设置定位回调监听
//val mLocationListener: AMapLocationListener? = null
//mLocationClient.setLocationListener(mLocationListener)
//val mLocationOption: AMapLocationClientOption = AMapLocationClientOption()
////初始化AMapLocationClientOption对象
//mLocationOption.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
//mLocationOption.isNeedAddress = true
////启动定位
//mLocationClient.startLocation()
////异步获取定位结果
//
//var mAMapLocationListener =
//    AMapLocationListener { amapLocation ->
//
//        if (amapLocation != null) {
//            if (amapLocation.errorCode == 0) {
//                binding.textViewLatLon.text = amapLocation.latitude.toString() + " " + amapLocation.longitude.toString()
//            }else {
//                //定位失败时，可通过ErrCode（错误码）信息来确定失败的原因，errInfo是错误信息，详见错误码表。
//                Log.e("AmapError","location Error, ErrCode:"
//                        + amapLocation.errorCode + ", errInfo:"
//                        + amapLocation.errorInfo
//                );
//            }
//        }
//    }