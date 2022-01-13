package com.qzlin.pastesimple.service

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import com.qzlin.pastesimple.Global
import com.qzlin.pastesimple.MainActivity
import com.qzlin.pastesimple.R
import com.qzlin.pastesimple.helper.Utility
import microsoft.aspnet.signalr.client.*
import microsoft.aspnet.signalr.client.hubs.HubConnection
import java.net.*
import java.util.*


class SignalRService : Service() {
//    private val tag = SignalRService::class.qualifiedName

    private lateinit var connection: HubConnection
    private lateinit var mHandler: Handler // to display Toast message
//    private var mHubProxy: HubProxy? = null


    private val binder = LocalBinder()

    var isClientRegistered: Boolean = false
    var connectButtonDown = false

    private lateinit var context: Context
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var notification: Notification

    lateinit var serverAddress: String
    var serverPort: Int? = null
    lateinit var uid: String
    var clientListenPort: Int? = null

    private val channelId: String = "ClipSyncServer" // The id of the channel.
    private val name: CharSequence = "ClipSyncServer" // The user-visible name of the channel.
    private val NOTIFICATION_TITLE: String = "ClipSync Working"
    private val NOTIFICATION_CONTENT_TEXT: String = "Copy Paste"

    private var pStopSelf: PendingIntent? = null

    private lateinit var icon: Bitmap

    private var pendingIntent: PendingIntent? = null
    private var mChannel: NotificationChannel? = null

    private lateinit var utility: Utility

    override fun onCreate() {
        super.onCreate()
        context = baseContext
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        utility = Utility(context)
        mHandler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i("test", "Sync Services Start")
        when (intent.action) {
            Global.START_SERVICE -> {
                serverAddress = intent.getStringExtra(Global.DATA_SERVER_ADDR)!!
                serverPort = intent.getIntExtra(Global.DATA_SERVER_PORT, 9999)
                clientListenPort = intent.getIntExtra(Global.DATA_CLIENT_PORT, 8888)
                uid = intent.getIntExtra(Global.DATA_CLIENT_UID, 0).toString()
                if (intent.getStringExtra("trigger") != "autostart")
                    connectButtonDown = true

                showNotification()
                startSync()
            }
            Global.STOP_SERVICE -> {
                server?.stop()
                connectButtonDown = false
                stopForeground(true)
                stopSelf()
            }
        }
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i("test","Sync Services Stopped")
        server?.stop()
        updateStatus("stopped")
        this.connectButtonDown = false
        mNotificationManager.cancel(Global.SIGNALR_SERVICE_NOTIFICATION_ID)
        super.onDestroy()
    }

//    override fun onUnbind(intent: Intent?): Boolean {
//        Log.d("Unbounding", "SignalRservice Service unbound")
//        return super.onUnbind(intent)
//    }


    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): SignalRService = this@SignalRService
    }

    private fun showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            mChannel = NotificationChannel(channelId, name, importance)
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
        val stopSelfIntent = Intent(this@SignalRService, SignalRService::class.java)
        stopSelfIntent.action = Global.STOP_SERVICE
        pStopSelf = PendingIntent.getService(
            context,
            Global.SIGNALR_SERVICE_NOTIFICATION_ID,
            stopSelfIntent,
            PendingIntent.FLAG_CANCEL_CURRENT
        )
        notification = NotificationCompat.Builder(this, "paste_simple")
            .setContentTitle(NOTIFICATION_TITLE)
            .setTicker(NOTIFICATION_TITLE)
            .setContentText(NOTIFICATION_CONTENT_TEXT)
            .setSmallIcon(R.drawable.clip_sync_logo_2)
            .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setChannelId(channelId)
            .addAction(android.R.drawable.ic_media_previous, "Stop", pStopSelf)
            .build()
        startForeground(Global.SIGNALR_SERVICE_NOTIFICATION_ID, notification)
    }

    var updateStatusLabel: Runnable? = null
    var statusText: String = ""
    private fun updateStatus(data: String) {
        statusText = data
        updateStatusLabel?.run()
//        val intent = Intent()
//        intent.action = Global.STATUS_UPDATE_ACTION
//        intent.putExtra("text", data)
//        this.sendBroadcast(intent)
    }

    private fun sendUDP(message: String, address: String, port: Int = 9999) {
        Thread {
            val socket = DatagramSocket()
            val sendData = message.toByteArray()
            val sendPacket = DatagramPacket(
                sendData,
                sendData.size,
                InetAddress.getByName(address), port
            )
            socket.send(sendPacket)
            Log.i("test", "send->$address:$port,$message")
        }.start()
    }

    fun sendCopiedText(text: String) {
        sendUDP("SYNC:$text", serverAddress)
    }

    class Server(var port: Int, var host: String = "0.0.0.0") {

        var receivedList: Queue<String> = LinkedList()
        var thread: Thread? = null
        var running = false
        private var socket: DatagramSocket? = null

        fun start() {
            running = true
            thread = Thread {
                while (true) {
                    val buffer = ByteArray(1024)
                    socket = DatagramSocket(port, InetAddress.getByName(host))
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket?.receive(packet)
                    } catch (e: SocketException) {
                        Log.e("test", "Socket Server", e)
                        if (!running)
                            break
                    }

                    val msg = String(packet.data, packet.offset, packet.length)
                    socket!!.close()
                    Log.i("test", "recv:$msg")

                    receivedList.offer(msg)
                    while (!receivedList.isEmpty())
                        onReceiveHandler?.run()
                }
            }
            thread!!.start()
            onStart?.run()
        }

        fun stop() {
            running = false
            socket?.close()
            thread?.interrupt()
        }

        var onStart: Runnable? = null
        fun connected(handler: Runnable) {
            this.onStart = handler
        }

        var onReceiveHandler: Runnable? = null
        fun onReceive(handler: Runnable) {
            this.onReceiveHandler = handler
        }
    }

    private fun register(clientUid: String) {
        sendUDP("REGISTER:$clientUid", serverAddress)
    }


    var server: Server? = null
    private fun startSync() {
        // Create a new console logger
        register(uid)
        server?.stop()
        server = Server(clientListenPort!!)
        Log.i("test", "start recv on $clientListenPort")

        server!!.connected {
            isClientRegistered = true
            notification = NotificationCompat.Builder(context, "status")
                .setContentTitle(NOTIFICATION_TITLE)
                .setTicker(NOTIFICATION_TITLE)
                .setContentText("Registered")
                .setSmallIcon(R.drawable.clip_sync_logo_2)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setChannelId(channelId)
                .addAction(android.R.drawable.ic_media_previous, "Stop", pStopSelf)
                .build()
            mNotificationManager.notify(
                Global.SIGNALR_SERVICE_NOTIFICATION_ID,
                notification
            )
            updateStatus("Client Registered")
        }

        server!!.start()
        return

        /*val logger = Logger { message, level -> Log.d("SignalR : ", message) }
        // Connect to the server
        val parameters =
            "&uid=" + utility.getUid() + "&platform=ANDROID" + "&device_id=" + UUID.randomUUID()
                .toString()
        val serverAddress = "http://" + utility.getServerAddress() + ":" + utility.getServerPort()
        connection = HubConnection(serverAddress, parameters, true, logger)

        // Create the hub proxy
        val proxy = connection.createHubProxy(Global.SignalHubName)
        mHubProxy = proxy

        val subscription = proxy.subscribe(Global.receive_copied_text_signalr_method_name)
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
                // handle ""text""
                    receivedText = receivedText.substring(1, receivedText.length - 1)

                val clip = ClipData.newPlainText(
                    System.currentTimeMillis().toString(), receivedText
                )
                Log.i("test", "set:$receivedText")

                Global.lastSetText = receivedText
                Global.waitCopyLoop = true

                clipboard.setPrimaryClip(clip)
                //TODO watermark
            })*/


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


        /*// Subscribe to the error event
        connection.error { error -> error.printStackTrace() }

        // Subscribe to the connected event
        connection.connected {
            isClientRegistered = true
            notification = NotificationCompat.Builder(context)
                .setContentTitle(NOTIFICATION_TITLE)
                .setTicker(NOTIFICATION_TITLE)
                .setContentText("Connecting...")
                .setSmallIcon(R.drawable.clip_sync_logo_2)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setChannelId(channelId)
                .addAction(android.R.drawable.ic_media_previous, "Stop", pStopSelf)
                .build()
            mNotificationManager.notify(
                Global.SIGNALR_SERVICE_NOTIFICATION_ID,
                notification
            )
            updateStatus("Connecting...")
        }

        // Subscribe to the closed event
        connection.closed {
            isClientRegistered = false
            notification = NotificationCompat.Builder(context)
                .setContentTitle(NOTIFICATION_TITLE)
                .setTicker(NOTIFICATION_TITLE)
                .setContentText("Disconnected")
                .setSmallIcon(R.drawable.clip_sync_logo_2)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setChannelId(channelId)
                .addAction(android.R.drawable.ic_media_previous, "Stop", pStopSelf)
                .build()
            mNotificationManager.notify(
                Global.SIGNALR_SERVICE_NOTIFICATION_ID,
                notification
            )
            updateStatus("Disconnected")
            //Auto reconnect if connect button down
            if (connectButtonDown) {
                updateStatus("Reconnecting")
//                startSignalR()
            }
        }

        // Start the connection
        connection.start().done {
            notification = NotificationCompat.Builder(context)
                .setContentTitle(NOTIFICATION_TITLE)
                .setTicker(NOTIFICATION_TITLE)
                .setContentText("Connected")
                .setSmallIcon(R.drawable.clip_sync_logo_2)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setChannelId(channelId)
                .addAction(android.R.drawable.ic_media_previous, "Stop", pStopSelf)
                .build()
            mNotificationManager.notify(
                Global.SIGNALR_SERVICE_NOTIFICATION_ID,
                notification
            )
            updateStatus("Connected")
        }

        // Subscribe to the received event
        connection.received { *//*json -> Log.i(tag, "RAW received message: $json")*//* }
    */
    }
}