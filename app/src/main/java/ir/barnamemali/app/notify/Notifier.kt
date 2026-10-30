package ir.barnamemali.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ir.barnamemali.app.MainActivity
import ir.barnamemali.app.R

class Notifier(private val context: Context) {
    private val sms = "sms"
    private val reminders = "reminders"
    private val budget = "budget"

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(sms, "پیامک‌های بانکی", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(reminders, "یادآوری سررسیدها", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(budget, "هشدار بودجه", NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun pendingSms(count: Int) = post(
        sms, 1, "${fa(count)} پیامک بانکی منتظر تأیید", "برای ثبت با یک لمس بزنید", MainActivity.ROUTE_SMS,
    )

    fun autoConfirmed(title: String, amount: String, detail: String) = post(
        sms, (System.currentTimeMillis() % 100000).toInt() + 1000, "ثبت خودکار: $title · $amount", detail, MainActivity.ROUTE_HOME,
    )

    fun reminder(title: String, text: String) = post(reminders, 2, title, text, MainActivity.ROUTE_OBLIGATIONS)

    fun budget(title: String, text: String) =
        post(budget, (title.hashCode() and 0xffff) + 3000, title, text, MainActivity.ROUTE_FAMILY)

    private fun post(channel: String, id: Int, title: String, text: String, route: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    private fun fa(n: Int) = ir.barnamemali.core.PersianText.toPersianDigits(n.toString())
}
