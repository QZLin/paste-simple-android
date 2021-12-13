package com.qzlin.pastesimple

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.qzlin.pastesimple.databinding.ActivityControlsBinding
import com.qzlin.pastesimple.helper.Utility
import com.qzlin.pastesimple.service.ClipBoardMonitor
import com.qzlin.pastesimple.service.SignalRService


class MainActivity : AppCompatActivity() {
/*    private lateinit var start_service_button: FloatingActionButton
    private lateinit var stop_service_button: FloatingActionButton
    private lateinit var logout_button: FloatingActionButton

    private lateinit var server_address_edit_text: EditText
    private lateinit var server_port_edit_text: EditText
    private lateinit var server_uid: EditText*/

    private lateinit var utility: Utility

    private val tag = MainActivity::class.java.simpleName

    //    private lateinit var uiHandler: Handler
    private lateinit var binding: ActivityControlsBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityControlsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        utility = Utility(this)
//        uiHandler = object : Handler(Looper.myLooper()!!) {
//            override fun handleMessage(msg: Message) {
//                Toast.makeText(applicationContext, msg.what, Toast.LENGTH_SHORT).show()
//                super.handleMessage(msg)
//            }
//        }
//        DataInterface.uiHandler = uiHandler

//        server_address_edit_text = findViewById(R.id.serverAddressEditText)
//        server_port_edit_text = findViewById(R.id.serverPortEditText)
//        server_uid = findViewById(R.id.serverUIDEditeText)

        binding.serverAddressEditText.setText(if (utility.getServerAddress() == null) "" else utility.getServerAddress())
        binding.serverAddressEditText.setText("abc");

        val server_port_value = utility.getServerPort()
        val server_uid_value = utility.getUid()
        binding.serverPortEditText.setText(if (server_port_value == 0) "" else server_port_value.toString())
        binding.serverUIDEditeText.setText(if (server_uid_value == 0) "" else server_uid_value.toString())

//        start_service_button = findViewById(R.id.activity_controls_service_start_fab)
//        stop_service_button = findViewById(R.id.activity_controls_service_stop_fab)
        binding.stopServicesButton.hide()
//        binding.startServicesButton.hide()
//        logout_button = findViewById(R.id.activity_controls_log_out_fab)

        binding.startServicesButton.setOnClickListener {
            if (binding.serverAddressEditText.text.isNotEmpty() && binding.serverPortEditText.text.isNotEmpty() && binding.serverUIDEditeText.text.isNotEmpty()
//                && binding.serverPortEditText.text.length > 1 && server_uid.text.length > 1
            ) {
                utility.setServerAddress(binding.serverAddressEditText.text.toString().trim())
                utility.setServerPort(binding.serverPortEditText.text.toString().trim().toInt())
                utility.setUid(binding.serverUIDEditeText.text.toString().trim().toInt())

                val signalRServiceIntent = Intent(applicationContext, SignalRService::class.java)
                signalRServiceIntent.action = Global.START_SERVICE
                startService(signalRServiceIntent)

                // Always Call after SignalR Service Started
                startService(Intent(applicationContext, ClipBoardMonitor::class.java))
                binding.startServicesButton.hide()
                binding.stopServicesButton.show()
            } else {
                binding.serverAddressEditText.error = "Enter Required Fields"
                binding.serverPortEditText.error = "Enter Required Fields"
                binding.serverUIDEditeText.error = "Enter Required Fields"
            }
        }
        binding.stopServicesButton.setOnClickListener {
            binding.stopServicesButton.hide()
            binding.startServicesButton.show()
            stopServices()
        }
//        binding.logoutServicesButton.setOnClickListener { logout() }
        binding.logoutServicesButton.setOnClickListener { _ -> logout() }

        receiveConnectionStatus()
    }

    lateinit var updateUIReceiver: BroadcastReceiver
    fun receiveConnectionStatus() {
        val filter = IntentFilter()
        filter.addAction(Global.STATUS_UPDATE_ACTION)

        updateUIReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                binding.textConnectStatus.text = intent?.getStringExtra("text") ?: ""
            }
        }
        registerReceiver(updateUIReceiver, filter)
    }

    private fun isServiceRunning(classgetName: String?): Boolean {
        val manager = (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            Log.e(tag, service.service.className)
            Log.e(tag, "ClassName" + service.service.className)
            if (classgetName == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun stopServices() {
        val signalRServiceIntent = Intent(applicationContext, SignalRService::class.java)
        signalRServiceIntent.action = Global.STOP_SERVICE
        stopService(signalRServiceIntent)

        // Always Call after SignalR Service Started
        stopService(Intent(applicationContext, ClipBoardMonitor::class.java))
    }

    /*override fun onResume() {
        super.onResume()
    }*/

    fun logout() {
        utility.clearUserPrefs()
        Toast.makeText(applicationContext, "Logged Out", Toast.LENGTH_SHORT).show()
        finishAffinity()
    }

    override fun onDestroy() {
        stopServices()
        unregisterReceiver(updateUIReceiver)
        super.onDestroy()
    }

    override fun onBackPressed() {
        finishAffinity()
    }
}