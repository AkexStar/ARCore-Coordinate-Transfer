package com.google.ar.core.ARPositioning.kotlin.Transfer

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.ar.core.ARPositioning.kotlin.Transfer.databinding.ActivityFirstBinding
import java.io.File

class FirstActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFirstBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFirstBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_first)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        // This example uses decor view, but you can use any visible view.
        // Hide the status bar.
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        // Remember that you should never show the action bar if the
        // status bar is hidden, so hide that too if necessary.
        actionBar?.hide()

        navView.setupWithNavController(navController)
    }

    override fun onDestroy() {
        val pref_xml = File("data/data/com.Alex.ARCore.ARPositioning/shared_prefs", "PROJECT.xml")
        if (pref_xml.exists()) {
            pref_xml.delete()
        }
        getSharedPreferences("PROJECT", Context.MODE_PRIVATE)?.edit{
            clear()
            commit()
        }
        super.onDestroy()
    }
}