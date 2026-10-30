package ir.barnamemali.app.sms

import android.content.Context
import android.provider.Telephony
import ir.barnamemali.app.data.Repository

/** Reads bank SMS already in the phone's inbox, e.g. the last few months on first run. */
object InboxImporter {
    suspend fun import(context: Context, repo: Repository, sinceMillis: Long): Int {
        var count = 0
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return 0
        cursor.use {
            while (it.moveToNext()) {
                val sender = it.getString(0) ?: ""
                val body = it.getString(1) ?: continue
                val date = it.getLong(2)
                if (repo.ingestSms(sender, body, date, notify = false)) count++
            }
        }
        return count
    }
}
