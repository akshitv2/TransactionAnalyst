package com.pulsefinance.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.pulsefinance.app.core.SmsMessage

/** Reads received SMS straight from the device's SMS provider. */
class SmsReader(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** All received messages, newest first. Call off the main thread. */
    fun readInbox(): List<SmsMessage> {
        val out = ArrayList<SmsMessage>()
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            while (cursor.moveToNext()) {
                val text = cursor.getString(body) ?: continue
                out.add(SmsMessage(cursor.getString(address), cursor.getLong(date), text))
            }
        }
        return out
    }
}
