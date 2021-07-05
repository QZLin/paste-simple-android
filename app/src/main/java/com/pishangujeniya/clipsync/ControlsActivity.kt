package com.pishangujeniya.clipsync

import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.pishangujeniya.clipsync.helper.Utility
import com.pishangujeniya.clipsync.service.ClipBoardMonitor
import com.pishangujeniya.clipsync.service.SignalRService

class ControlsActivity : AppCompatActivity() {
    private lateinit var start_service_button: FloatingActionButton
    private lateinit var stop_service_button: FloatingActionButton
    private lateinit var logout_button: FloatingActionButton

    private lateinit var server_address_edit_text: EditText
    private lateinit var server_port_edit_text: EditText
    private lateinit var server_uid: EditText

    private lateinit var utility: Utility

    private val tag = ControlsActivity::class.java.simpleName
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_controls)

        utility = Utility(this)
        server_address_edit_text = findViewById(R.id.serverAddressEditText)
        server_port_edit_text = findViewById(R.id.serverPortEditText)
        server_uid = findViewById(R.id.serverUIDEditeText)

        server_address_edit_text.setText(if (utility.getServerAddress() == null) "" else utility!!.getServerAddress())
        val server_port_value = utility.getServerPort()
        val server_uid_value = utility.getUid()
        server_port_edit_text.setText(if (server_port_value == 0) "" else server_port_value.toString())

        server_uid.setText(if (server_uid_value == 0) "" else server_uid_value.toString())

        start_service_button = findViewById(R.id.activity_controls_service_start_fab)
        stop_service_button = findViewById(R.id.activity_controls_service_stop_fab)
        logout_button = findViewById(R.id.activity_controls_log_out_fab)
        start_service_button.setOnClickListener(View.OnClickListener {
            if (server_address_edit_text.text != null && server_address_edit_text.text
                    .isNotEmpty() && server_port_edit_text.text != null && server_port_edit_text.text
                    .toString().length > 1 && server_uid.text != null && server_uid.text
                    .toString().length > 1
            ) {
                utility.setServerAddress(
                    server_address_edit_text.text.toString().trim { it <= ' ' })
                utility.setServerPort(
                    server_port_edit_text.text.toString().trim { it <= ' ' }.toInt()
                )
                utility.setUid(server_uid.text.toString().trim { it <= ' ' }.toInt())
                val signalRServiceIntent = Intent(this@ControlsActivity, SignalRService::class.java)
                signalRServiceIntent.action = GlobalValues.START_SERVICE
                startService(signalRServiceIntent)

                // Always Call after SignalR Service Started
                startService(Intent(this@ControlsActivity, ClipBoardMonitor::class.java))
                start_service_button.hide()
                stop_service_button.show()
            } else {
                server_address_edit_text.error = "Enter Required Fields"
                server_port_edit_text.error = "Enter Required Fields"
                server_uid.error = "Enter Required Fields"
            }
        })
        stop_service_button.setOnClickListener(View.OnClickListener {
            stop_service_button.hide()
            start_service_button.show()
            stopServices()
        })
        logout_button.setOnClickListener(View.OnClickListener { logout() })
    }

    private fun isServiceRunning(classgetName: String?): Boolean {
        val manager = (getSystemService(ACTIVITY_SERVICE) as ActivityManager)!!
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
        val signalRServiceIntent = Intent(this@ControlsActivity, SignalRService::class.java)
        signalRServiceIntent.action = GlobalValues.STOP_SERVICE
        stopService(signalRServiceIntent)

        // Always Call after SignalR Service Started
        stopService(Intent(this@ControlsActivity, ClipBoardMonitor::class.java))
    }

    /*override fun onResume() {
        super.onResume()
    }*/

    private fun logout() {
        utility.clearUserPrefs()
        Toast.makeText(applicationContext, "Logged Out", Toast.LENGTH_SHORT).show()
        finishAffinity()
    }

    override fun onDestroy() {
        stopServices()
        super.onDestroy()
    }

    override fun onBackPressed() {
        finishAffinity()
    }
}