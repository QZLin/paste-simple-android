package com.pishangujeniya.clipsync.service

import android.app.Service
import android.content.*
import android.content.ClipboardManager.OnPrimaryClipChangedListener
import android.os.Environment
import android.os.IBinder
import android.text.TextUtils
import android.util.Log
import com.pishangujeniya.clipsync.GlobalValues
import com.pishangujeniya.clipsync.helper.Utility
import com.pishangujeniya.clipsync.service.SignalRService.LocalBinder
import java.io.*
import java.net.URLEncoder
import java.util.*
import java.util.concurrent.Executors

/*
* Copyright 2013 Tristan Waddington
*
*    Licensed under the Apache License, Version 2.0 (the "License");
*    you may not use this file except in compliance with the License.
*    You may obtain a copy of the License at
*
*        http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS,
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*    See the License for the specific language governing permissions and
*    limitations under the License.
*/
//Thanks to the code author
/**
 * Monitors the [ClipboardManager] for changes and logs the text to a file.
 */
class ClipBoardMonitor : Service() {
    private var utility: Utility? = null
    private var mHistoryFile: File? = null
    private val mThreadPool = Executors.newSingleThreadExecutor()
    private var mClipboardManager: ClipboardManager? = null
    private var mBound = false
    private var mService: SignalRService? = null
    private val mConnection: ServiceConnection? = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName?, service: IBinder?) {
            Log.e(TAG, "Inside service connected - Activity ")
            // We've bound to SignalRService, cast the IBinder and get SignalRService instance
            val binder = service as LocalBinder?
            mService = binder?.getService() as SignalRService
            mBound = true
            Log.e(TAG, "bound status - $mBound")
        }

        override fun onServiceDisconnected(arg0: ComponentName?) {
            mService = null
            mBound = false
            Log.e(TAG, "bound disconnected - status - $mBound")
        }
    }

    override fun onCreate() {
        super.onCreate()

        // TODO: Show an ongoing notification when this service is running.
        utility = Utility(applicationContext)
        mHistoryFile = File(getExternalFilesDir(null), FILENAME)
        mClipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        mClipboardManager!!.addPrimaryClipChangedListener(
                mOnPrimaryClipChangedListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val mIntent = Intent(this, SignalRService::class.java)
        bindService(mIntent, mConnection!!, BIND_AUTO_CREATE)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        super.onDestroy()
        mClipboardManager?.removePrimaryClipChangedListener(
                mOnPrimaryClipChangedListener)

        // Unbind from the service
        if (mBound) {
            unbindService(mConnection!!)
            mBound = false
            Log.e(TAG, "bound disconnecting - status - $mBound")
        }
        if (mService != null) {
            mService!!.onDestroy()
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    private val mOnPrimaryClipChangedListener: OnPrimaryClipChangedListener? = OnPrimaryClipChangedListener {
        Log.d(TAG, "onPrimaryClipChanged")
        //                    mThreadPool.execute(new WriteHistoryRunnable(
//                            clip.getItemAt(0).getText()));
        if (!mClipboardManager?.hasPrimaryClip()!!) {
            Log.e(TAG, "no Primary Clip")
        } else if (!mClipboardManager?.getPrimaryClipDescription()?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)!!) {
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
            val clip = mClipboardManager!!.getPrimaryClip()
            val copied_content = clip?.getItemAt(0)?.text.toString()
            if (copied_content != GlobalValues.lastSetText || !GlobalValues.waitCopyLoop) {
                Log.i("test", "clip:$copied_content") //TODO watermark
                if (mService != null) {
                    var encoded: String
                    try {
                        encoded = URLEncoder.encode(copied_content, "utf-8").replace("+", "%20")
                    } catch (e: UnsupportedEncodingException) {
                        encoded = copied_content
                        Log.e("test", e.toString())
                    }
                    mService!!.sendCopiedText(encoded)
                }
            } else {
                GlobalValues.waitCopyLoop = false
            }


//                        Log.e(TAG, "Content at 0 " + copied_content);
            /* if (copied_content.contains(GlobalValues.copied_water_mark)) {
                        // Means Copied text already copied by ClipSync and came back again so don't send again
                    } else {
                        Log.e(TAG, "Copied Text : " + copied_content);
                        if (mService != null) {
//                            sendNotification(copied_content);
                            mService.sendCopiedText(copied_content);
                        }
                    }*/
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
                    Log.w(TAG, String.format("Failed to open file %s for writing!",
                            mHistoryFile!!.absoluteFile))
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
        private val FILENAME: String? = "clipboard-history.txt"
        private const val NOTIFICATION_ID = 777
    }
}