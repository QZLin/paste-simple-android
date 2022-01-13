package com.qzlin.pastesimple.service

import android.app.Service
import android.content.*
import android.content.ClipboardManager.OnPrimaryClipChangedListener
import android.os.Binder
import android.os.Environment
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import com.qzlin.pastesimple.Global
import com.qzlin.pastesimple.helper.Utility
import com.qzlin.pastesimple.service.SignalRService.LocalBinder
import java.io.*
import java.util.*


/*
*   Origin file information: Copyright 2013 Tristan Waddington, under the Apache License, Version 2.0
*   Full LICENSE information of this file: LICENSE_ClipBoardMonitor.txt
*   This file is already modified
*/
/**
 * Monitors the [ClipboardManager] for changes and logs the text to a file.
 */
class ClipBoardMonitor : Service() {
    private lateinit var utility: Utility
    private lateinit var mHistoryFile: File
    private lateinit var mClipboardManager: ClipboardManager

    //    private val mThreadPool = Executors.newSingleThreadExecutor()
    private var serviceBond = false

    private lateinit var syncService: SignalRService
    private val connection: ServiceConnection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as SignalRService.LocalBinder
            syncService = binder.getService()
            serviceBond = true
            Log.i(TAG, "bound status - $serviceBond")
        }

        override fun onServiceDisconnected(arg0: ComponentName?) {
            serviceBond = false
            Log.i(TAG, "bound disconnected - status - $serviceBond")
        }
    }

    override fun onCreate() {
        super.onCreate()
        // TODO: Show an ongoing notification when this service is running.
        utility = Utility(applicationContext)
        mHistoryFile = File(getExternalFilesDir(null), FILENAME)
        mClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        mClipboardManager.addPrimaryClipChangedListener(
            mOnPrimaryClipChangedListener
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mIntent = Intent(this, SignalRService::class.java)
        bindService(mIntent, connection, BIND_AUTO_CREATE)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mClipboardManager.removePrimaryClipChangedListener(
            mOnPrimaryClipChangedListener
        )

        // Unbind from the service
        if (serviceBond) {
            unbindService(connection)
            serviceBond = false
            Log.i(TAG, "bound disconnecting - status - $serviceBond")
        }
    }
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): ClipBoardMonitor = this@ClipBoardMonitor
    }

    override fun onBind(p0: Intent): IBinder {
        return LocalBinder()
    }


    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    private val mOnPrimaryClipChangedListener: OnPrimaryClipChangedListener =
        OnPrimaryClipChangedListener {
            Log.d(TAG, "onPrimaryClipChanged")
            if (!mClipboardManager.hasPrimaryClip()) {
                Log.e(TAG, "no Primary Clip")
            } else if (!mClipboardManager.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)!!
            ) {
                assert(true)
                /*
                        // since the clipboard has data but it is not plain text
                        //since the clipboard contains plain text.
                        ClipData clip = mClipboardManager.getPrimaryClip();
                        String copied_content = clip.getItemAt(0).getText().toString();
                        Log.i("test", "clip:" + copied_content);
    //                        Log.e(TAG,"Content at 0 "+copied_content);
                        if (copied_content.contains(GlobalValues.copied_water_mark)) {
                            // Means Copied text already copied by ClipSync and came back again so don't send again
                        } else {
                            Log.e(TAG, "Copied Text : " + copied_content);
                            if (mService != null) {
    //                            sendNotification(copied_content);
                                mService.sendCopiedText(copied_content);
                            }
                        }*/
            } else {
                //since the clipboard contains plain text.
                val clipData = mClipboardManager.primaryClip
                val copiedContent = clipData?.getItemAt(0)?.text.toString()
                Log.i("test", "clip_update:$copiedContent")
                if (Global.lastSetClip == null || Global.lastSetClip?.description?.label != clipData?.description?.label) {
                    syncService.sendCopiedText(copiedContent)
//                    Global.lastSetClip = clipData
                }
            }
        }

    private inner class WriteHistoryRunnable(text: CharSequence?) : Runnable {
        private val mNow: Date?
        private val mTextToWrite: CharSequence?
        override fun run() {
            if (TextUtils.isEmpty(mTextToWrite)) {
                // Don't write empty text to the file
                return
            }
            if (isExternalStorageWritable()) {
                try {
                    Log.i(TAG, "Writing new clip to history:")
                    Log.i(TAG, mTextToWrite.toString())
                    val writer = BufferedWriter(FileWriter(mHistoryFile, true))
                    writer.write(String.format("[%s]: ", mNow.toString()))
                    writer.write(mTextToWrite.toString())
                    writer.newLine()
                    writer.close()
                } catch (e: IOException) {
                    Log.w(
                        TAG, String.format(
                            "Failed to open file %s for writing!",
                            mHistoryFile.absoluteFile
                        )
                    )
                }
            } else {
                Log.w(TAG, "External storage is not writable!")
            }
        }

        init {
            mNow = Date(System.currentTimeMillis())
            mTextToWrite = text
        }
    }

    companion object {
        private val TAG = ClipBoardMonitor::class.java.simpleName
        private val FILENAME: String = "clipboard-history.txt"
        private const val NOTIFICATION_ID = 777
    }
}