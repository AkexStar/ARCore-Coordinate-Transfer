package com.google.ar.core.ARPositioning.kotlin.Transfer.ui.home

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.ar.core.ARPositioning.kotlin.Transfer.MainActivity
import com.google.ar.core.ARPositioning.kotlin.Transfer.databinding.FragmentHomeBinding
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {
    companion object{
        const val FILE_TIME_PATTERN = "MMdd-HHmmss"
    }

    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

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
        binding.buttonSetProject.setOnClickListener {
            val str = binding.editText.text.toString()
            if (str == ""){
                Toast.makeText(activity, "请输入项目名称！", Toast.LENGTH_SHORT).show()
            }else {
                val dateFormat = SimpleDateFormat(FILE_TIME_PATTERN, Locale.PRC).format(
                    Date()
                ).toString()
                activity?.getSharedPreferences("PROJECT_NAME",Context.MODE_PRIVATE)?.edit{
                    putString("name", str)
                }
                activity?.getSharedPreferences("PROJECT_NAME",Context.MODE_PRIVATE)?.edit{
                    putString("time", dateFormat)
                }
                Toast.makeText(activity, "工程已建立！", Toast.LENGTH_SHORT).show()
                binding.textHome.text = "当前工程：$str，创建时间：$dateFormat"
            }
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}