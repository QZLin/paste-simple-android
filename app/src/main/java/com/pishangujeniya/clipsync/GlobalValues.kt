package com.pishangujeniya.clipsync

object GlobalValues {
    @JvmField
    var SignalHubName = "SignalRHub"

    @JvmField
    var receive_copied_text_signalr_method_name = "ReceiveCopiedText"

    @JvmField
    var send_copied_text_signalr_method_name = "SendCopiedText"

    @JvmField
    var copied_water_mark = "- Copied By ClipSync"

    @JvmField
    var STOP_SERVICE = "STOP_SERVICE"

    @JvmField
    var START_SERVICE = "START_SERVICE"

    @JvmField
    var SIGNALR_SERVICE_NOTIFICATION_ID = 1001


    @JvmField
    var lastSetText = ""

    @JvmField
    var waitCopyLoop = false
}