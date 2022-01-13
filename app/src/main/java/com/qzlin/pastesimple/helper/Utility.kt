package com.qzlin.pastesimple.helper

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import java.util.*

class Utility(var context: Context) {
    private val userSharedPref: SharedPreferences = context.getSharedPreferences("USER", Context.MODE_PRIVATE)

    fun getPermissions(): Array<String?> {
        return arrayOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
    }

    fun isDataAvailable(): Boolean {
        val conMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = conMgr.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }

    fun setUid(uid: Int) {
        val editor = userSharedPref.edit()
        editor.putInt("UID", uid)
        editor.apply()
    }

    fun getUid(): Int {
        return userSharedPref.getInt("UID", 0)
    }

    fun setServerAddress(address: String) {
        val editor = userSharedPref.edit()
        editor.putString("SERVER_ADDRESS", address)
        editor.apply()
    }

    fun getServerAddress(): String? {
        return userSharedPref.getString("SERVER_ADDRESS", null)
    }

    fun setServerPort(port: Int) {
        val editor = userSharedPref.edit()
        editor?.putInt("SERVER_PORT", port)
        editor?.apply()
    }

    fun getServerPort(): Int {
        return userSharedPref.getInt("SERVER_PORT", 0)
    }

    fun setuserName(userName: String) {
        val editor = userSharedPref.edit()
        editor.putString("USERNAME", userName)
        editor.apply()
    }

    fun getuserName(): String? {
        return userSharedPref.getString("USERNAME", null)
    }

    fun setEmail(email: String) {
        val editor = userSharedPref.edit()
        editor.putString("EMAIL", email)
        editor.apply()
    }

    fun getEmail(): String? {
        return userSharedPref.getString("EMAIL", null)
    }

    fun clearUserPrefs() {
        val editor = userSharedPref.edit()
        editor?.clear()
        editor?.apply()
    }

    fun getLastClipboardText(): String {
        val mClipboardManager =
            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        if (!mClipboardManager.hasPrimaryClip()) {
            return ""
        } else if (!mClipboardManager.primaryClipDescription?.hasMimeType(
                ClipDescription.MIMETYPE_TEXT_PLAIN
            )!!
        ) {

            // since the clipboard has data but it is not plain text
            //since the clipboard contains plain text.
            val clip = mClipboardManager.primaryClip!!
            return clip.getItemAt(0).text.toString()
        } else {

            //since the clipboard contains plain text.
            val clip = mClipboardManager.primaryClip!!
            return clip.getItemAt(0).text.toString()
        }
    }


}