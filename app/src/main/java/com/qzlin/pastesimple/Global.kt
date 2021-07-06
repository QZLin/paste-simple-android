package com.qzlin.pastesimple

object Global {
    const val SignalHubName = "SignalRHub"
    const val receive_copied_text_signalr_method_name = "ReceiveCopiedText"
    const val send_copied_text_signalr_method_name = "SendCopiedText"
    val copied_water_mark = "- Copied By ClipSync"
    const val STOP_SERVICE = "STOP_SERVICE"
    const val START_SERVICE = "START_SERVICE"
    const val SIGNALR_SERVICE_NOTIFICATION_ID = 1001

    const val STATUS_UPDATE_ACTION = "com.qzlin.pastesimple.action"

    var lastSetText = ""
    var waitCopyLoop = false
}