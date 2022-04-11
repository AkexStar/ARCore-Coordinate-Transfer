package com.google.ar.core.ARPositioning.kotlin.Transfer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.anychart.APIlib
import com.anychart.AnyChart
import com.anychart.AnyChartView
import com.anychart.chart.common.dataentry.DataEntry
import com.anychart.chart.common.dataentry.ValueDataEntry
import com.anychart.charts.Cartesian
import com.anychart.charts.Scatter
import com.anychart.core.scatter.series.Line
import com.anychart.core.scatter.series.Marker
import com.anychart.enums.HoverMode
import com.anychart.enums.MarkerType
import com.anychart.enums.TooltipDisplayMode
import com.google.ar.core.ARPositioning.kotlin.Transfer.R
import java.io.BufferedReader
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStreamReader


class DataViewActivity : AppCompatActivity() {
    lateinit var anyChartViewXY: AnyChartView
    lateinit var anyChartViewTX: AnyChartView
    lateinit var anyChartViewTY: AnyChartView
    lateinit var scatter_xy: Scatter
    lateinit var projectName: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_view)
        projectName= getSharedPreferences("PROJECT", Context.MODE_PRIVATE).getString("name","") as String
        val button_current = findViewById<Button>(R.id.button_current) as Button
        val button3 = findViewById<Button>(R.id.button3) as Button
        anyChartViewXY = findViewById<AnyChartView>(R.id.any_chart_view_xy)
        if (projectName == "")
            button_current.visibility = View.INVISIBLE

//        scatter_xy = AnyChart.scatter()
//        anyChartViewXY.setChart(scatter_xy)
//        anyChartViewTX = findViewById<AnyChartView>(R.id.any_chart_view_tx)
//        anyChartViewTY = findViewById<AnyChartView>(R.id.any_chart_view_ty)

        button3.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "text/*"
            intent.flags= Intent.FLAG_GRANT_READ_URI_PERMISSION
            startActivityForResult(Intent.createChooser(intent, "选取文件"), 1)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when(requestCode){
            1 -> {
                val fileUri = data?.data //The uri with the location of the file
                if (fileUri != null) {
                    Toast.makeText(this, fileUri.toString(), Toast.LENGTH_SHORT).show()
                    drawChart(fileUri)
                } else {
                    val msg = "Null filename data received!"
                    val toast = Toast.makeText(this, msg, Toast.LENGTH_LONG)
                    toast.show()
                }
            }
        }
    }

    fun drawChart(fileUri: Uri){
//        anyChartViewXY.clear()
        scatter_xy = AnyChart.scatter()
        anyChartViewXY.setChart(scatter_xy)
//        scatter_xy.animation(true)
        val data: MutableList<DataEntry> = ArrayList()
        Log.e("fileUri",fileUri.toString())
        val contentResolver = applicationContext.contentResolver
        try {
            contentResolver.openInputStream(fileUri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        if(!line.startsWith('-')){
                            val lineSplit = line.split('\t',' ')
                            val x = lineSplit[1].toDouble()
                            val y = lineSplit[3].toDouble()
                            data.add(ValueDataEntry(x,y))
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
//        scatter_xy.interactivity()
//            .hoverMode(HoverMode.BY_SPOT)
//            .spotRadius(15.0)
//        scatter_xy.tooltip().displayMode(TooltipDisplayMode.UNION)
        scatter_xy.line(data)
//        anyChartViewXY.setChart(scatter_xy)
    }

    fun onClickDrawCurrentProject(view: View?){
        scatter_xy = AnyChart.scatter()
        anyChartViewXY.setChart(scatter_xy)
        val data_xy: MutableList<DataEntry> = ArrayList()
        var timeNumer: Int = 1
        try {
            if (projectName == ""){
                Toast.makeText(this, "当前工程还未建立", Toast.LENGTH_SHORT).show()
                return
            }
            val input = openFileInput("$projectName-CamData.txt")
            val reader = BufferedReader(InputStreamReader(input))
            reader.use {
                reader.forEachLine {
                    if(!it.startsWith('-')){
                        val lineSplit = it.split('\t',' ')
                        val x = lineSplit[1].toDouble()
                        val y = - lineSplit[3].toDouble()
                        data_xy.add(ValueDataEntry(x,y))
                        timeNumer += 1
                    }
                }
            }
            Toast.makeText(this, "Data Size is:"+data_xy.size.toString(), Toast.LENGTH_SHORT).show()
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            Toast.makeText(this, "无法打开文件！", Toast.LENGTH_SHORT).show()
        }
        scatter_xy.line(data_xy)
    }
}