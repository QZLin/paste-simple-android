package com.qzlin.pastesimple

import android.app.Activity
import android.app.ActivityManager
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.qzlin.pastesimple.databinding.MainActivityBinding
import com.qzlin.pastesimple.helper.Utility
import com.qzlin.pastesimple.service.ClipBoardMonitor
import com.qzlin.pastesimple.service.SignalRService


class MainActivity : AppCompatActivity() {
    private lateinit var utility: Utility

    //    private lateinit var uiHandler: Handler
    lateinit var binding: MainActivityBinding
    private lateinit var syncServices: SignalRService
    private var syncServBond = false

    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as SignalRService.LocalBinder
            syncServices = binder.getService()
            syncServBond = true

            syncServices.server?.onReceive {
                val receivedText = syncServices.server?.receivedList?.poll()
                if (receivedText.isNullOrEmpty())
                    return@onReceive
                val p = Regex("(.*?):((?:.|\n|\r)*)")
                val r = p.matchEntire(receivedText) ?: return@onReceive
                val command = r.groups[1]!!.value
                val content = r.groups[2]?.value ?: ""
                Log.i("test", "Parse:$command/$content")
                if (content.isEmpty())
                    return@onReceive

                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(
                    System.currentTimeMillis().toString(), content
                )
                clipboard.setPrimaryClip(clip)
                Log.i("test", "set_clip:$content")
                Global.lastSetClip = clip
            }
            syncServices.updateStatusLabel = Runnable {
                binding.textConnectStatus.text = syncServices.statusText
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            syncServBond = false
        }
    }

    private fun getViewStrID(v: View): String {
        return resources.getResourceEntryName(v.id)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        utility = Utility(this)
        val sharedPreferences = this.getSharedPreferences("USER", Activity.MODE_PRIVATE)
        val editor = sharedPreferences.edit()

        fun saveTextData(v: TextView) {
            editor.putString(getViewStrID(v), v.text.toString())
            editor.apply()
        }

        fun loadTextData(v: TextView) {
            v.text = sharedPreferences.getString(getViewStrID(v), "")
        }
        loadTextData(binding.serverAddressEditText)
        loadTextData(binding.serverPortEditText)
        loadTextData(binding.uidEditeText)
        loadTextData(binding.clientPortEditText)

        binding.stopServicesButton.hide()


        binding.startServicesButton.setOnClickListener {
            if (binding.serverAddressEditText.text.isNotEmpty() && binding.serverPortEditText.text.isNotEmpty() && binding.uidEditeText.text.isNotEmpty()
            ) {
                saveTextData(binding.serverAddressEditText)
                saveTextData(binding.serverPortEditText)
                saveTextData(binding.uidEditeText)
                saveTextData(binding.clientPortEditText)

                val serverPort = binding.serverPortEditText.text.toString().toInt()
                val clientListenPort = binding.clientPortEditText.text.toString().toInt()
                val clientUid = binding.uidEditeText.text.toString().toInt()

                val syncServIntent =
                    Intent(applicationContext, SignalRService::class.java).also { intent ->
                        bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    }
                syncServIntent.action = Global.START_SERVICE
                syncServIntent.putExtra(
                    Global.DATA_SERVER_ADDR,
                    binding.serverAddressEditText.text.toString()
                )
                syncServIntent.putExtra(Global.DATA_SERVER_PORT, serverPort)
                syncServIntent.putExtra(Global.DATA_CLIENT_PORT, clientListenPort)
                syncServIntent.putExtra(Global.DATA_CLIENT_UID, clientUid)
                startService(syncServIntent)


                // Always Call after SignalR Service Started
                val clipboardMIntent = Intent(applicationContext, ClipBoardMonitor::class.java)
                startService(clipboardMIntent)
                binding.startServicesButton.hide()
                binding.stopServicesButton.show()


            } else {
                binding.serverAddressEditText.error = "Enter Server Address"
                binding.serverPortEditText.error = "Enter Server Port"
                binding.uidEditeText.error = "Enter UID"
            }

        }
        binding.stopServicesButton.setOnClickListener {
            stopSyncServices()
            binding.stopServicesButton.hide()
            binding.startServicesButton.show()
        }
        binding.logoutServicesButton.setOnClickListener { logout() }

//        receiveConnectionStatus()
        binding.startServicesButton.performClick()
    }

    /*lateinit var updateUIReceiver: BroadcastReceiver
    private fun receiveConnectionStatus() {
        val filter = IntentFilter()
        filter.addAction(Global.STATUS_UPDATE_ACTION)

        updateUIReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                binding.textConnectStatus.text = intent?.getStringExtra("text") ?: ""
            }
        }
        registerReceiver(updateUIReceiver, filter)
    }*/

    /*private fun isServiceRunning(classgetName: String?): Boolean {
        val manager = (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            Log.e(tag, service.service.className)
            Log.e(tag, "ClassName" + service.service.className)
            if (classgetName == service.service.className) {
                return true
            }
        }
        return false
    }*/

    private fun stopSyncServices() {
        val signalRServiceIntent = Intent(applicationContext, SignalRService::class.java)
        stopService(signalRServiceIntent)

        stopService(Intent(applicationContext, ClipBoardMonitor::class.java))
        unbindService(connection)
        syncServBond = false
    }


    private fun logout() {
//        utility.clearUserPrefs()
        Toast.makeText(applicationContext, "Logged Out", Toast.LENGTH_SHORT).show()
        finishAffinity()
    }

    override fun onDestroy() {
        stopSyncServices()
        unbindService(connection)
        syncServBond = false
        super.onDestroy()
    }

    override fun onBackPressed() {
        finishAffinity()
    }
}