package com.qzlin.pastesimple.service

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.qzlin.pastesimple.MainActivity
import com.qzlin.pastesimple.GlobalValues
import com.qzlin.pastesimple.R
import com.qzlin.pastesimple.helper.Utility
import microsoft.aspnet.signalr.client.*
import microsoft.aspnet.signalr.client.hubs.HubConnection
import microsoft.aspnet.signalr.client.hubs.HubProxy
import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.util.*

class SignalRService : Service() {
    private val tag = SignalRService::class.java.simpleName

    private lateinit var connection: HubConnection
    private lateinit var mHandler: Handler // to display Toast message
    private var mHubProxy: HubProxy? = null


    private val mBinder: LocalBinder = LocalBinder()

    var is_service_connected: Boolean = false

    private lateinit var context: Context
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var notification: Notification

    private val CHANNEL_ID: String = "ClipSyncServer" // The id of the channel.
    private val name: CharSequence = "ClipSyncServer" // The user-visible name of the channel.
    private val NOTIFICATION_TITLE: String = "ClipSync Working"
    private val NOTIFICATION_CONTENT_TEXT: String = "Copy Paste"

    private var pStopSelf: PendingIntent? = null

    private lateinit var icon: Bitmap

    private var pendingIntent: PendingIntent? = null
    private var mChannel: NotificationChannel? = null

    private lateinit var utility: Utility
    var looperThreadCreated = false
    override fun onCreate() {
        super.onCreate()
//        Log.d("service", "Inside oncreate  - service")

//         context = this.applicationContext
        context = baseContext
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        utility = Utility(context)
        mHandler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (GlobalValues.STOP_SERVICE == intent!!.action) {
//            Log.d(tag, "called to cancel service")
            stopForeground(true)
            stopSelf()
            mNotificationManager.cancel(GlobalValues.SIGNALR_SERVICE_NOTIFICATION_ID)
        } else if (GlobalValues.START_SERVICE == intent.action) {
            showNotification()
            startSignalR()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        try {
            if (connection.state.compareTo(ConnectionState.Connected) > -1) {
                connection.stop()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mNotificationManager.cancel(GlobalValues.SIGNALR_SERVICE_NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d("Unbounding", "SignalRservice Service unbound")
        return super.onUnbind(intent)
    }

    override fun onBind(intent: Intent?): IBinder {
        // Return the communication channel to the service.
        Log.d("service", "onBind  - service")
        //        startSignalR();
        return mBinder
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        fun getService(): SignalRService {
            // Return this instance of SignalRService so clients can call public methods
            return this@SignalRService
        }
    }

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            mNotificationManager.createNotificationChannel(mChannel!!)
        }
        val notificationIntent = Intent(this, MainActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            notificationIntent, 0
        )
        icon = BitmapFactory.decodeResource(
            resources,
            R.drawable.clip_sync_logo_2
        )
        val stop_self_intent = Intent(this@SignalRService, SignalRService::class.java)
        stop_self_intent.action = GlobalValues.STOP_SERVICE
        pStopSelf = PendingIntent.getService(
            context,
            GlobalValues.SIGNALR_SERVICE_NOTIFICATION_ID,
            stop_self_intent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )
        notification = NotificationCompat.Builder(this)
            .setContentTitle(NOTIFICATION_TITLE)
            .setTicker(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_CONTENT_TEXT)
            .setSmallIcon(R.drawable.clip_sync_logo_2)
            .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setChannelId(CHANNEL_ID)
            .addAction(android.R.drawable.ic_media_previous, "Stop", pStopSelf)
            .build()
        startForeground(GlobalValues.SIGNALR_SERVICE_NOTIFICATION_ID, notification)
    }

    fun sendCopiedText(text: String?) {
        if (is_service_connected) {
            Log.e(tag, "Sending Copied Text to SignalR")
            mHubProxy?.invoke(GlobalValues.send_copied_text_signalr_method_name, text)
            Log.i("test", "send:$text")
        } else {
            Log.e(tag, "Service is not connected so not sending copied text")
        }
    }

    private fun startSignalR() {
        // Create a new console logger
        val logger = Logger { message, level -> Log.d("SignalR : ", message) }
        // Connect to the server
        val parameters =
            "&uid=" + utility.getUid() + "&platform=ANDROID" + "&device_id=" + UUID.randomUUID()
                .toString()
        val serverAddress = "http://" + utility.getServerAddress() + ":" + utility.getServerPort()
        connection = HubConnection(serverAddress, parameters, true, logger)

        // Create the hub proxy
        val proxy = connection.createHubProxy(GlobalValues.SignalHubName)
        mHubProxy = proxy

        val subscription = proxy.subscribe(GlobalValues.receive_copied_text_signalr_method_name)
        subscription.addReceivedHandler(
            Action { eventParameters ->
                if (eventParameters == null || eventParameters.isEmpty()) {
                    return@Action
                }

                if (!looperThreadCreated) {
                    Looper.prepare()
                    looperThreadCreated = true
                }

                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                var receivedText: String = eventParameters[0].toString()
                try {
                    receivedText = URLDecoder.decode(receivedText, "utf-8")
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                }

                if (receivedText.length > 2)
                    receivedText =
                        receivedText.substring(1, receivedText.length - 1)// handle ""text""

                val clip =
                    ClipData.newPlainText(System.currentTimeMillis().toString(), receivedText)
                Log.i("test", "set:$receivedText")

                GlobalValues.lastSetText = receivedText
                GlobalValues.waitCopyLoop = true

                clipboard.setPrimaryClip(clip)
                //TODO watermark
            })


        /*proxy.subscribe(new Object() {
            @SuppressWarnings("unused")
            public void recieveIncomingChat(RecieveIncomingchats recieveIncomingchats) {
                MainFragment.receivedincommingchats(recieveIncomingchats);
                Log.d("hit:", "Hit on receive Incoming chats");
            }
            @SuppressWarnings("unused")
            public void serviceStatus(boolean temp){
                Log.d("service_status", "status called");
            }
        });*/


        // Subscribe to the error event
        connection.error(ErrorCallback { error -> error.printStackTrace() })

        // Subscribe to the connected event
        connection.connected(Runnable {
//            println("Connecting...")
            is_service_connected = true
            notification = NotificationCompat.Builder(context)
                .setContentTitle(NOTIFICATION_TITLE)
                .setTicker(NOTIFICATION_TITLE)
                .setContentText("Connecting...")
                .setSmallIcon(R.drawable.clip_sync_logo_2)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setChannelId(CHANNEL_ID)
                .addAction(android.R.drawable.ic_media_previous, "Stop", pStopSelf)
                .build()
            mNotificationManager.notify(
                GlobalValues.SIGNALR_SERVICE_NOTIFICATION_ID,
                notification
            )
        })

        // Subscribe to the closed event
        connection.closed(Runnable {
            println("DISCONNECTED")
            notification = NotificationCompat.Builder(context)
                .setContentTitle(NOTIFICATION_TITLE)
                .setTicker(NOTIFICATION_TITLE)
                .setContentText("Disconnected")
                .setSmallIcon(R.drawable.clip_sync_logo_2)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setChannelId(CHANNEL_ID)
                .addAction(android.R.drawable.ic_media_previous, "Stop", pStopSelf)
                .build()
            mNotificationManager.notify(
                GlobalValues.SIGNALR_SERVICE_NOTIFICATION_ID,
                notification
            )
        })

        // Start the connection
        connection.start().done {
            println("Connected")
            notification = NotificationCompat.Builder(context)
                .setContentTitle(NOTIFICATION_TITLE)
                .setTicker(NOTIFICATION_TITLE)
                .setContentText("Connected")
                .setSmallIcon(R.drawable.clip_sync_logo_2)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setChannelId(CHANNEL_ID)
                .addAction(android.R.drawable.ic_media_previous, "Stop", pStopSelf)
                .build()
            mNotificationManager.notify(
                GlobalValues.SIGNALR_SERVICE_NOTIFICATION_ID,
                notification
            )
        }

        // Subscribe to the received event
        connection.received(MessageReceivedHandler { json -> println("RAW received message: $json") })
    }
}