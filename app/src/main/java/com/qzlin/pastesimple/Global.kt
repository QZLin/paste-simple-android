package com.qzlin.pastesimple

import android.content.ClipData

object Global {
    const val SignalHubName = "SignalRHub"
    const val receive_copied_text_signalr_method_name = "ReceiveCopiedText"
    const val send_copied_text_signalr_method_name = "SendCopiedText"

    //    val copied_water_mark = "- Copied By ClipSync"
    const val STOP_SERVICE = "STOP_SERVICE"
    const val INIT_SERVICE = "STOP_SERVICE"
    const val START_SERVICE = "START_SERVICE"
    const val SIGNALR_SERVICE_NOTIFICATION_ID = 1001

    const val DATA_SERVER_ADDR = "server-addr"
    const val DATA_SERVER_PORT = "server-port"
    const val DATA_CLIENT_PORT = "client-port"
    const val DATA_CLIENT_UID = "client-uid"

    const val STATUS_UPDATE_ACTION = "com.qzlin.pastesimple.update_status"

    //    var lastSetText = ""
//    var waitCopyLoop = false
    var lastSetClip: ClipData? = null
}