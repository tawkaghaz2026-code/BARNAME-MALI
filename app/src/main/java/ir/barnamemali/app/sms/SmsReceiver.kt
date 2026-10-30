package ir.barnamemali.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ir.barnamemali.app.BarnameMaliApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Receives new SMS. Only messages that look like bank transactions are kept; everything else is dropped. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        // A long SMS arrives in several parts; join them per sender.
        val bySender = parts.groupBy { it.originatingAddress ?: "" }
            .mapValues { (_, list) -> list.joinToString("") { it.messageBody ?: "" } to (list.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()) }
        val pending = goAsync()
        val repo = (context.applicationContext as BarnameMaliApp).repository
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                for ((sender, value) in bySender) repo.ingestSms(sender, value.first, value.second)
            } finally {
                pending.finish()
            }
        }
    }
}
